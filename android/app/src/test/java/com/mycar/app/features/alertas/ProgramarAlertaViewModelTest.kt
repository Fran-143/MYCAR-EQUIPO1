package com.mycar.app.features.alertas

import com.mycar.app.features.alertas.data.ProgramarAlertaDto
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.ContextoProgramarAlerta
import com.mycar.app.features.alertas.domain.CriterioAlerta
import com.mycar.app.features.alertas.domain.DatosAlerta
import com.mycar.app.features.alertas.domain.EstadoOperacionAlerta
import com.mycar.app.features.alertas.domain.ReferenciaMantenimientoAlerta
import com.mycar.app.features.alertas.domain.ResultadoOperacionIncierto
import com.mycar.app.features.alertas.domain.TipoAlerta
import com.mycar.app.features.alertas.viewmodel.BorradorAlerta
import com.mycar.app.features.alertas.viewmodel.PasoProgramar
import com.mycar.app.features.alertas.viewmodel.ProgramarAlertaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramarAlertaViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun main() { Dispatchers.setMain(dispatcher) }
    @After fun clean() { Dispatchers.resetMain() }

    private class RepoFake(
        var km: Int? = 50000,
        var fecha: String = "2026-10-28",
        var referencias: List<ReferenciaMantenimientoAlerta> = emptyList(),
        var estadoOperacion: String = "CONFIRMADA",
        var idAlertaOperacion: Long? = 77L
    ) : AlertasRepository {
        var programaciones = 0
        var consultasOperacion = 0
        val idsOperacion = mutableListOf<String>()
        var ultimaSolicitud: ProgramarAlertaDto? = null
        var fallo: Exception? = null
        override suspend fun contexto(idVehiculo: Long) = ContextoProgramarAlerta(
            fecha, km, if (km != null) 1L else null,
            listOf(
                TipoAlerta("CAMBIO_ACEITE", "Cambio de aceite"),
                TipoAlerta("CAMBIO_FILTROS", "Cambio de filtros"),
                TipoAlerta("REVISION_FRENOS", "Revisión de frenos")
            ),
            referencias, true
        )
        override suspend fun listar(idVehiculo: Long) = 0 to emptyList<DatosAlerta>()
        override suspend fun programar(idVehiculo: Long, solicitud: ProgramarAlertaDto): Long {
            idsOperacion += solicitud.idOperacion
            ultimaSolicitud = solicitud
            fallo?.let { ex ->
                if (ex is ResultadoOperacionIncierto) {
                    throw ResultadoOperacionIncierto(solicitud.idOperacion, ex.message ?: "en proceso")
                }
                throw ex
            }
            programaciones++
            return programaciones.toLong()
        }
        override suspend fun operacion(idOperacion: String): EstadoOperacionAlerta {
            consultasOperacion++
            return EstadoOperacionAlerta(estadoOperacion, idAlertaOperacion)
        }
    }

    @Test fun tresAlertasConsecutivasValidasConNuevaIdentidadCadaUna() = runTest(dispatcher) {
        val repo = RepoFake()
        val vm = ProgramarAlertaViewModel(repo, 9L)
        advanceUntilIdle()
        repeat(3) { n ->
            if (n > 0) vm.prepararNuevaProgramacion()
            advanceUntilIdle()
            assertNull(vm.estado.value.exito)
            vm.actualizar {
                it.copy(codigoTipo = "CAMBIO_ACEITE", criterio = "POR_KILOMETRAJE", intervaloKm = "${5000 + n * 1000}")
            }
            vm.revisar()
            advanceUntilIdle()
            assertEquals(PasoProgramar.REVISION, vm.estado.value.paso)
            vm.confirmar()
            advanceUntilIdle()
            assertEquals((n + 1).toLong(), vm.estado.value.exito)
            assertEquals(PasoProgramar.EXITO, vm.estado.value.paso)
        }
        assertEquals(3, repo.programaciones)
        assertEquals(3, repo.idsOperacion.distinct().size)
    }

    @Test fun reintentoConservaMismaOperacionSinDuplicarIdentidad() = runTest(dispatcher) {
        val repo = RepoFake()
        repo.fallo = IllegalStateException("Fallo temporal")
        val vm = ProgramarAlertaViewModel(repo, 9L)
        advanceUntilIdle()
        vm.actualizar { it.copy(codigoTipo = "CAMBIO_FILTROS", intervaloKm = "8000") }
        vm.revisar()
        advanceUntilIdle()
        vm.confirmar()
        advanceUntilIdle()
        assertNull(vm.estado.value.exito)
        assertNotNull(vm.estado.value.idOperacion)
        val primera = vm.estado.value.idOperacion
        repo.fallo = null
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(1L, vm.estado.value.exito)
        assertEquals(listOf(primera, primera), repo.idsOperacion)
    }

    @Test fun prepararNuevaLimpiaExitoYPermiteAbrirDeNuevo() = runTest(dispatcher) {
        val repo = RepoFake()
        val vm = ProgramarAlertaViewModel(repo, 3L)
        advanceUntilIdle()
        vm.actualizar { it.copy(codigoTipo = "REVISION_FRENOS", intervaloKm = "10000") }
        vm.revisar(); advanceUntilIdle()
        vm.confirmar(); advanceUntilIdle()
        assertNotNull(vm.estado.value.exito)
        vm.prepararNuevaProgramacion()
        advanceUntilIdle()
        assertNull(vm.estado.value.exito)
        assertEquals(PasoProgramar.FORMULARIO, vm.estado.value.paso)
        assertNotNull(vm.estado.value.contexto)
    }

    @Test fun ambosExigeTiempoYKmYCalculaObjetivoDesdeKmVigente() = runTest(dispatcher) {
        val repo = RepoFake(km = 58240)
        val vm = ProgramarAlertaViewModel(repo, 2L)
        advanceUntilIdle()
        assertTrue(CriterioAlerta.usaTiempo("AMBOS") && CriterioAlerta.usaKm("AMBOS"))
        vm.actualizar {
            it.copy(
                codigoTipo = "CAMBIO_ACEITE", criterio = "AMBOS",
                cantidadTiempo = "6", unidadTiempo = "MESES", intervaloKm = "5000"
            )
        }
        assertTrue(ProgramarAlertaViewModel.validarBorrador(vm.estado.value.borrador, 58240).isEmpty())
        vm.revisar(); advanceUntilIdle()
        assertEquals(63240, vm.estado.value.resumen?.kmObjetivo)
        vm.confirmar(); advanceUntilIdle()
        assertEquals(1L, vm.estado.value.exito)
        assertEquals(58240, repo.ultimaSolicitud?.kmBaseConfirmado)
        assertNotNull(repo.ultimaSolicitud?.fechaBaseConfirmada)
        assertEquals(5000, repo.ultimaSolicitud?.intervaloKm)
        assertEquals(6, repo.ultimaSolicitud?.cantidadTiempo)
    }

    @Test fun porTiempoSinKmNoExigeIntervalo() {
        val e = ProgramarAlertaViewModel.validarBorrador(
            BorradorAlerta(codigoTipo = "CAMBIO_ACEITE", criterio = "POR_TIEMPO", cantidadTiempo = "3"),
            kmActual = null
        )
        assertTrue(e.isEmpty())
    }

    @Test fun ambosSinKmSeRechazaEnValidacion() {
        val e = ProgramarAlertaViewModel.validarBorrador(
            BorradorAlerta(codigoTipo = "CAMBIO_ACEITE", criterio = "AMBOS", cantidadTiempo = "3", intervaloKm = "5000"),
            kmActual = null
        )
        assertTrue(e.containsKey("intervaloKm"))
    }

    @Test fun g89ClienteRecalculaSiKmCambioAntesDeConfirmar() = runTest(dispatcher) {
        val repo = RepoFake(km = 50000)
        val vm = ProgramarAlertaViewModel(repo, 4L)
        advanceUntilIdle()
        vm.actualizar { it.copy(codigoTipo = "CAMBIO_ACEITE", intervaloKm = "5000") }
        vm.revisar(); advanceUntilIdle()
        assertEquals(55000, vm.estado.value.resumen?.kmObjetivo)
        repo.km = 51000
        vm.confirmar(); advanceUntilIdle()
        assertTrue(vm.estado.value.basesCambiaron)
        assertEquals(PasoProgramar.REVISION, vm.estado.value.paso)
        assertEquals(56000, vm.estado.value.resumen?.kmObjetivo)
        assertEquals(0, repo.programaciones)
    }

    @Test fun validacionExigeTipoYNoBloqueaSinMensajeOculto() {
        val e = ProgramarAlertaViewModel.validarBorrador(BorradorAlerta())
        assertTrue(e.containsKey("codigoTipo"))
    }

    @Test fun cambiarDatosInvalidaOperacionAnterior() = runTest(dispatcher) {
        val repo = RepoFake()
        repo.fallo = IllegalStateException("x")
        val vm = ProgramarAlertaViewModel(repo, 1L)
        advanceUntilIdle()
        vm.actualizar { it.copy(codigoTipo = "CAMBIO_ACEITE", intervaloKm = "5000") }
        vm.revisar(); advanceUntilIdle()
        vm.confirmar(); advanceUntilIdle()
        val op = vm.estado.value.idOperacion
        assertNotNull(op)
        repo.fallo = null
        vm.actualizar { it.copy(intervaloKm = "7000") }
        assertNull(vm.estado.value.idOperacion)
        assertNotEquals(op, vm.estado.value.idOperacion)
        assertFalse(CriterioAlerta.usaTiempo("POR_KILOMETRAJE"))
    }

    @Test fun resumenEnviaIdMantenimientoReferenciaMostrada() = runTest(dispatcher) {
        val repo = RepoFake(
            referencias = listOf(
                ReferenciaMantenimientoAlerta(55L, "CAMBIO_ACEITE", "2026-09-01", null)
            )
        )
        val vm = ProgramarAlertaViewModel(repo, 2L)
        advanceUntilIdle()
        vm.actualizar { it.copy(codigoTipo = "CAMBIO_ACEITE", intervaloKm = "5000") }
        vm.revisar(); advanceUntilIdle()
        assertEquals(55L, vm.estado.value.borrador.idReferencia)
        assertEquals(55L, vm.estado.value.resumen?.referencia?.id)
        vm.confirmar(); advanceUntilIdle()
        assertEquals(55L, repo.ultimaSolicitud?.idMantenimientoReferencia)
    }

    @Test fun resultadoInciertoRecuperaConfirmadaSinSegundoEnvio() = runTest(dispatcher) {
        val repo = RepoFake(estadoOperacion = "CONFIRMADA", idAlertaOperacion = 88L)
        repo.fallo = ResultadoOperacionIncierto("op-fija")
        val vm = ProgramarAlertaViewModel(repo, 6L)
        advanceUntilIdle()
        vm.actualizar { it.copy(codigoTipo = "CAMBIO_ACEITE", intervaloKm = "5000") }
        vm.revisar(); advanceUntilIdle()
        // Fuerza identidad conocida antes del envío fallido.
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(PasoProgramar.EXITO, vm.estado.value.paso)
        assertEquals(88L, vm.estado.value.exito)
        assertEquals(0, repo.programaciones)
        assertTrue(repo.consultasOperacion >= 1)
    }

    @Test fun overflowKmSeRechazaEnValidacion() {
        val e = ProgramarAlertaViewModel.validarBorrador(
            BorradorAlerta(codigoTipo = "CAMBIO_ACEITE", intervaloKm = "100"),
            kmActual = Int.MAX_VALUE - 10
        )
        assertTrue(e.containsKey("intervaloKm"))
        assertNull(ProgramarAlertaViewModel.sumarKmSeguro(Int.MAX_VALUE - 10, 100))
        assertEquals(63240, ProgramarAlertaViewModel.sumarKmSeguro(58240, 5000))
    }
}
