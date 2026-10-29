package com.mycar.app.features.vehiculos

import com.mycar.app.features.vehiculos.data.ContextoKilometrajeDto
import com.mycar.app.features.vehiculos.data.EstadoOperacionKmDto
import com.mycar.app.features.vehiculos.data.ResultadoActualizarKmDto
import com.mycar.app.features.vehiculos.data.SolicitudActualizarKmDto
import com.mycar.app.features.vehiculos.domain.ActualizarKilometrajeRepository
import com.mycar.app.features.vehiculos.viewmodel.ActualizarKilometrajeViewModel
import com.mycar.app.features.vehiculos.viewmodel.PasoKm
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActualizarKilometrajeReaperturaTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun main() { Dispatchers.setMain(dispatcher) }
    @After fun clean() { Dispatchers.resetMain() }

    private class Repo(
        var km: Int = 1000,
        var fallar: Boolean = false,
        var registros: Int = 0,
        var estadoOperacion: String = "CONFIRMADA",
        var consultasOperacion: Int = 0,
        var ultimoIdRegistrado: UUID? = null
    ) : ActualizarKilometrajeRepository {
        override suspend fun contexto(idVehiculo: Long) =
            ContextoKilometrajeDto(idVehiculo, 11L, km, "2026-10-28T12:00:00Z", 1, "ACTIVO")
        override suspend fun registrar(idVehiculo: Long, solicitud: SolicitudActualizarKmDto): ResultadoActualizarKmDto {
            ultimoIdRegistrado = solicitud.idOperacion
            if (fallar) throw IllegalStateException("Fallo de red")
            registros++
            km = solicitud.kmNuevo
            return ResultadoActualizarKmDto(solicitud.idOperacion, idVehiculo, 12L, km, "2026-10-28", "CONFIRMADA")
        }
        override suspend fun operacion(idOperacion: UUID): EstadoOperacionKmDto {
            consultasOperacion++
            val resultado = if (estadoOperacion == "CONFIRMADA") {
                ResultadoActualizarKmDto(idOperacion, 5L, 12L, km, "2026-10-28", "CONFIRMADA")
            } else null
            return EstadoOperacionKmDto(idOperacion, estadoOperacion, resultado)
        }
    }

    @Test fun abrirNoEnviaActualizacion() = runTest(dispatcher) {
        val repo = Repo()
        val vm = ActualizarKilometrajeViewModel(5L, repo)
        advanceUntilIdle()
        assertEquals(PasoKm.EDICION, vm.estado.value.paso)
        assertEquals(0, repo.registros)
    }

    @Test fun nuevaInstanciaTrasExitoVuelveAEdicion() = runTest(dispatcher) {
        val repo = Repo()
        val primero = ActualizarKilometrajeViewModel(5L, repo)
        advanceUntilIdle()
        primero.cambiarKm("1500")
        primero.irVistaPrevia()
        primero.confirmar()
        advanceUntilIdle()
        assertEquals(PasoKm.EXITO, primero.estado.value.paso)
        assertEquals(1, repo.registros)

        // Simula nueva apertura (clave de sesión distinta → nuevo ViewModel).
        val segundo = ActualizarKilometrajeViewModel(5L, repo)
        advanceUntilIdle()
        assertEquals(PasoKm.EDICION, segundo.estado.value.paso)
        assertNull(segundo.estado.value.kmConfirmado)
        assertEquals(1500, segundo.estado.value.contexto?.kmActual)
        assertNotEquals(PasoKm.EXITO, segundo.estado.value.paso)
    }

    @Test fun errorNoSePresentaComoExitoYPermiteReintentarFormulario() = runTest(dispatcher) {
        val repo = Repo(fallar = true)
        val vm = ActualizarKilometrajeViewModel(8L, repo)
        advanceUntilIdle()
        vm.cambiarKm("2000")
        vm.irVistaPrevia()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(PasoKm.ERROR, vm.estado.value.paso)
        assertTrue(vm.estado.value.mensaje != null)
        repo.fallar = false
        vm.cargarContexto()
        advanceUntilIdle()
        assertEquals(PasoKm.EDICION, vm.estado.value.paso)
    }

    @Test fun cancelarAntesDeGuardarNoRegistra() = runTest(dispatcher) {
        val repo = Repo()
        val vm = ActualizarKilometrajeViewModel(2L, repo)
        advanceUntilIdle()
        vm.cambiarKm("1800")
        vm.irVistaPrevia()
        assertEquals(PasoKm.VISTA_PREVIA, vm.estado.value.paso)
        assertEquals(0, repo.registros)
    }

    @Test fun cambiarKmConOperacionPendienteNoDescartaSiYaConfirmo() = runTest(dispatcher) {
        val repo = Repo(fallar = true, km = 1000)
        val vm = ActualizarKilometrajeViewModel(5L, repo)
        advanceUntilIdle()
        vm.cambiarKm("1500")
        vm.irVistaPrevia()
        vm.confirmar()
        advanceUntilIdle()
        val opPendiente = vm.estado.value.idOperacion
        assertNotNull(opPendiente)
        assertEquals(PasoKm.ERROR, vm.estado.value.paso)

        // El servidor sí guardó; solo se perdió la respuesta.
        repo.fallar = false
        repo.km = 1500
        repo.estadoOperacion = "CONFIRMADA"
        vm.cambiarKm("1600")
        advanceUntilIdle()

        assertEquals(PasoKm.EXITO, vm.estado.value.paso)
        assertEquals(1500, vm.estado.value.kmConfirmado)
        assertEquals(0, repo.registros)
        assertTrue(repo.consultasOperacion >= 1)
    }

    @Test fun cambiarKmConOperacionEnProcesoConservaIdentidad() = runTest(dispatcher) {
        val repo = Repo(fallar = true)
        val vm = ActualizarKilometrajeViewModel(5L, repo)
        advanceUntilIdle()
        vm.cambiarKm("1500")
        vm.irVistaPrevia()
        vm.confirmar()
        advanceUntilIdle()
        val op = vm.estado.value.idOperacion
        assertNotNull(op)

        repo.estadoOperacion = "EN_PROCESO"
        vm.cambiarKm("1600")
        advanceUntilIdle()

        assertEquals(op, vm.estado.value.idOperacion)
        assertEquals("1500", vm.estado.value.kmNuevoTexto)
        assertEquals(PasoKm.ERROR, vm.estado.value.paso)
        assertEquals(0, repo.registros)
    }

    @Test fun reintentoMismoEnvioConservaIdentidad() = runTest(dispatcher) {
        val repo = Repo(fallar = true)
        val vm = ActualizarKilometrajeViewModel(5L, repo)
        advanceUntilIdle()
        vm.cambiarKm("1500")
        vm.irVistaPrevia()
        vm.confirmar()
        advanceUntilIdle()
        val primera = vm.estado.value.idOperacion
        assertNotNull(primera)
        val idEnviado = repo.ultimoIdRegistrado
        assertEquals(primera, idEnviado)

        repo.fallar = false
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(primera, idEnviado)
        assertEquals(1, repo.registros)
        assertEquals(PasoKm.EXITO, vm.estado.value.paso)
        assertEquals(primera, repo.ultimoIdRegistrado)
    }
}
