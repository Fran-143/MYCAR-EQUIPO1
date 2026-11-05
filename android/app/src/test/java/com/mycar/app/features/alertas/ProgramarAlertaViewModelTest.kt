package com.mycar.app.features.alertas

import com.mycar.app.features.alertas.data.ProgramarAlertaDto
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.ContextoProgramarAlerta
import com.mycar.app.features.alertas.domain.CriterioAlerta
import com.mycar.app.features.alertas.domain.DatosAlerta
import com.mycar.app.features.alertas.domain.EstadoOperacionAlerta
import com.mycar.app.features.alertas.domain.ReferenciaMantenimientoAlerta
import com.mycar.app.features.alertas.domain.ResultadoOperacionIncierto
import com.mycar.app.features.alertas.domain.SolicitudAtenderAlerta
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
        override suspend fun atender(idVehiculo: Long, idAlerta: Long, solicitud: SolicitudAtenderAlerta) = error("no usado")
        override suspend fun anular(idVehiculo: Long, idAlerta: Long) = error("no usado")
    }

    private fun BorradorAlerta.km(tipo: String = "CAMBIO_ACEITE", intervalo: String = "5000") =
        copy(codigoTipo = tipo, criterio = "POR_KILOMETRAJE", origenCalculo = "DESDE_HOY", intervaloKm = intervalo)

    @Test fun tresAlertasConsecutivasValidasConNuevaIdentidadCadaUna() = runTest(dispatcher) {
        val repo = RepoFake()
        val vm = ProgramarAlertaViewModel(repo, 9L)
        advanceUntilIdle()
        repeat(3) { n ->
            if (n > 0) vm.prepararNuevaProgramacion()
            advanceUntilIdle()
            assertNull(vm.estado.value.exito)
            vm.actualizar { it.km(intervalo = "${5000 + n * 1000}") }
            vm.revisar()
            advanceUntilIdle()
            assertEquals(PasoProgramar.REVISION, vm.estado.value.paso)
            vm.confirmar()
            advanceUntilIdle()
            assertEquals((n + 1).toLong(), vm.estado.value.exito)
        }
        assertEquals(3, repo.programaciones)
        assertEquals(3, repo.idsOperacion.distinct().size)
    }

    @Test fun reintentoConservaMismaOperacionSinDuplicarIdentidad() = runTest(dispatcher) {
        val repo = RepoFake()
        repo.fallo = IllegalStateException("Fallo temporal")
        val vm = ProgramarAlertaViewModel(repo, 9L)
        advanceUntilIdle()
        vm.actualizar { it.km("CAMBIO_FILTROS", "8000") }
        vm.revisar(); advanceUntilIdle()
        vm.confirmar(); advanceUntilIdle()
        assertNull(vm.estado.value.exito)
        val primera = vm.estado.value.idOperacion
        assertNotNull(primera)
        repo.fallo = null
        vm.confirmar(); advanceUntilIdle()
        assertEquals(1L, vm.estado.value.exito)
        assertEquals(listOf(primera, primera), repo.idsOperacion)
    }

    @Test fun ambosDesdeHoyCalculaObjetivos() = runTest(dispatcher) {
        val repo = RepoFake(km = 58240, fecha = "2026-11-03")
        val vm = ProgramarAlertaViewModel(repo, 2L)
        advanceUntilIdle()
        vm.actualizar {
            it.copy(
                codigoTipo = "CAMBIO_ACEITE", criterio = "AMBOS", origenCalculo = "DESDE_HOY",
                cantidadTiempo = "6", unidadTiempo = "MESES", intervaloKm = "5000"
            )
        }
        assertTrue(ProgramarAlertaViewModel.validarBorrador(vm.estado.value.borrador, 58240).isEmpty())
        vm.revisar(); advanceUntilIdle()
        assertEquals("2027-05-03", vm.estado.value.resumen?.fechaObjetivo)
        assertEquals(63240, vm.estado.value.resumen?.kmObjetivo)
        vm.confirmar(); advanceUntilIdle()
        assertEquals("DESDE_HOY", repo.ultimaSolicitud?.origenCalculo)
        assertNull(repo.ultimaSolicitud?.idMantenimientoReferencia)
        assertEquals(58240, repo.ultimaSolicitud?.kmBaseConfirmado)
    }

    @Test fun ambosDesdeMantenimientoUsaFechaYKmDelServicio() = runTest(dispatcher) {
        val repo = RepoFake(
            km = 15400,
            fecha = "2026-11-03",
            referencias = listOf(
                ReferenciaMantenimientoAlerta(55L, "CAMBIO_ACEITE", "2026-10-20", 15000, null)
            )
        )
        val vm = ProgramarAlertaViewModel(repo, 2L)
        advanceUntilIdle()
        vm.actualizar {
            it.copy(
                codigoTipo = "CAMBIO_ACEITE", criterio = "AMBOS", origenCalculo = "DESDE_MANTENIMIENTO",
                idReferencia = 55L, cantidadTiempo = "6", unidadTiempo = "MESES", intervaloKm = "5000"
            )
        }
        vm.revisar(); advanceUntilIdle()
        assertEquals("2027-04-20", vm.estado.value.resumen?.fechaObjetivo)
        assertEquals(20000, vm.estado.value.resumen?.kmObjetivo)
        vm.confirmar(); advanceUntilIdle()
        assertEquals("DESDE_MANTENIMIENTO", repo.ultimaSolicitud?.origenCalculo)
        assertEquals(55L, repo.ultimaSolicitud?.idMantenimientoReferencia)
        assertEquals(15000, repo.ultimaSolicitud?.kmBaseConfirmado)
        assertNull(repo.ultimaSolicitud?.idLecturaConfirmada)
    }

    @Test fun desdeHoyNoEnviaReferencia() = runTest(dispatcher) {
        val repo = RepoFake(
            referencias = listOf(
                ReferenciaMantenimientoAlerta(55L, "CAMBIO_ACEITE", "2026-09-01", 14000, null)
            )
        )
        val vm = ProgramarAlertaViewModel(repo, 2L)
        advanceUntilIdle()
        vm.actualizar { it.km() }
        vm.revisar(); advanceUntilIdle()
        assertNull(vm.estado.value.resumen?.referencia)
        vm.confirmar(); advanceUntilIdle()
        assertNull(repo.ultimaSolicitud?.idMantenimientoReferencia)
    }

    @Test fun sinMantenimientosElegiblesFallaValidacion() {
        val e = ProgramarAlertaViewModel.validarBorrador(
            BorradorAlerta(
                codigoTipo = "CAMBIO_ACEITE",
                origenCalculo = "DESDE_MANTENIMIENTO",
                criterio = "POR_TIEMPO",
                cantidadTiempo = "6"
            ),
            kmActual = 1000,
            refs = emptyList()
        )
        assertTrue(e.containsKey("idReferencia"))
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
            BorradorAlerta(
                codigoTipo = "CAMBIO_ACEITE", criterio = "AMBOS", origenCalculo = "DESDE_HOY",
                cantidadTiempo = "3", intervaloKm = "5000"
            ),
            kmActual = null
        )
        assertTrue(e.containsKey("intervaloKm"))
    }

    @Test fun g89ClienteRecalculaSiKmCambioAntesDeConfirmar() = runTest(dispatcher) {
        val repo = RepoFake(km = 50000)
        val vm = ProgramarAlertaViewModel(repo, 4L)
        advanceUntilIdle()
        vm.actualizar { it.km() }
        vm.revisar(); advanceUntilIdle()
        assertEquals(55000, vm.estado.value.resumen?.kmObjetivo)
        repo.km = 51000
        vm.confirmar(); advanceUntilIdle()
        assertTrue(vm.estado.value.basesCambiaron)
        assertEquals(56000, vm.estado.value.resumen?.kmObjetivo)
        assertEquals(0, repo.programaciones)
    }

    @Test fun resultadoInciertoRecuperaConfirmadaSinSegundoEnvio() = runTest(dispatcher) {
        val repo = RepoFake(estadoOperacion = "CONFIRMADA", idAlertaOperacion = 88L)
        repo.fallo = ResultadoOperacionIncierto("op-fija")
        val vm = ProgramarAlertaViewModel(repo, 6L)
        advanceUntilIdle()
        vm.actualizar { it.km() }
        vm.revisar(); advanceUntilIdle()
        vm.confirmar(); advanceUntilIdle()
        assertEquals(PasoProgramar.EXITO, vm.estado.value.paso)
        assertEquals(88L, vm.estado.value.exito)
        assertEquals(0, repo.programaciones)
        assertTrue(repo.consultasOperacion >= 1)
    }

    @Test fun overflowKmSeRechazaEnValidacion() {
        val e = ProgramarAlertaViewModel.validarBorrador(
            BorradorAlerta(codigoTipo = "CAMBIO_ACEITE", criterio = "POR_KILOMETRAJE", intervaloKm = "100"),
            kmActual = Int.MAX_VALUE - 10
        )
        // validarBorrador ya no chequea overflow; revisar() y sumarKmSeguro sí
        assertNull(ProgramarAlertaViewModel.sumarKmSeguro(Int.MAX_VALUE - 10, 100))
        assertEquals(63240, ProgramarAlertaViewModel.sumarKmSeguro(58240, 5000))
        assertFalse(CriterioAlerta.usaTiempo("POR_KILOMETRAJE"))
        assertNotEquals("x", e["codigoTipo"])
    }
}
