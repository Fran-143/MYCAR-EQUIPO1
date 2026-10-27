package com.mycar.app.features.historial.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mycar.app.features.historial.domain.*
import com.mycar.app.features.mantenimiento.data.*
import com.mycar.app.features.mantenimiento.domain.MantenimientoRepository
import com.mycar.app.ui.theme.MyCarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Datos de prueba solo en androidTest. Estas pruebas no autentican ni guardan en PostgreSQL. */
class HistorialMantenimientoPantallasTest {
    @get:Rule val compose = createComposeRule()
    private val activo = VehiculoHistorial(10, "ABC123", "Toyota", "Corolla", "ACTIVO", true)
    private val inactivo = VehiculoHistorial(11, "DEF456", "Nissan", "Versa", "INACTIVO", false)
    private val registros = listOf(
        MantenimientoHistorial(21, "FRENOS", "Revisión de frenos", null, LocalDate.of(2026, 10, 10), 49000, "ANULADO", BigDecimal.ZERO),
        MantenimientoHistorial(20, "CAMBIO_ACEITE", "Cambio de aceite", null, LocalDate.of(2026, 9, 20), 47000, "REALIZADO", null))

    private fun mostrar(e: EstadoHistorial, seleccionar: (Long) -> Unit = {}, registrar: (Long) -> Unit = {},
        reintentar: () -> Unit = {}, volver: () -> Unit = {}) {
        compose.setContent {
            MyCarTheme {
                HistorialMantenimientoScreen(e, volver, seleccionar, reintentar, {}, registrar,
                    registroDisponible = true)
            }
        }
    }

    @Test fun tarjetasDistinguenCostoNullCeroKmHistoricoYEstado() {
        mostrar(EstadoHistorial(FaseHistorial.LISTO, listOf(activo), activo, registros))
        compose.onNodeWithTag("rf18.contador").assertTextEquals("2 registros")
        compose.onNodeWithText("Costo: S/ 0.00").assertExists()
        compose.onNodeWithTag("rf18.registro.20").performScrollTo()
        compose.onNodeWithText("Costo: No registrado").assertExists()
        compose.onNodeWithText("20/09/2026 · 47 000 km").assertExists()
        compose.onNodeWithTag("rf18.detalle.20").assertIsNotEnabled()
        compose.onNodeWithTag("rf18.registro.21").performScrollTo()
        compose.onNodeWithText("Anulado").assertExists()
    }

    @Test fun selectorEntregaVehiculoElegidoSinAsignarloPrincipal() {
        var seleccionado: Long? = null
        mostrar(EstadoHistorial(FaseHistorial.SIN_SELECCION, listOf(activo.copy(principal = false), inactivo)),
            seleccionar = { seleccionado = it })
        compose.onNodeWithTag("rf18.selector").performClick()
        compose.onNodeWithTag("rf18.vehiculo.11").performClick()
        compose.runOnIdle { assertEquals(11L, seleccionado); assertTrue(!inactivo.principal) }
    }

    @Test fun inactivoSeConsultaPeroRegistrarPermaneceBloqueadoYPestanasFuturasTambien() {
        mostrar(EstadoHistorial(FaseHistorial.LISTO, listOf(inactivo), inactivo, registros))
        compose.onNodeWithTag("rf18.registrar").assertIsNotEnabled()
        compose.onNodeWithTag("rf18.tab.km").assertIsNotEnabled()
        compose.onNodeWithTag("rf18.tab.talleres").assertIsNotEnabled()
        compose.onNodeWithTag("rf18.registro.21").assertExists()
    }

    @Test fun errorOcultaTarjetasPreviasYOfreceReintentoManual() {
        var reintentos = 0
        mostrar(EstadoHistorial(FaseHistorial.ERROR_HISTORIAL, listOf(activo), activo, registros,
            mensaje = "Error controlado"), reintentar = { reintentos++ })
        compose.onNodeWithTag("rf18.contador").assertDoesNotExist()
        compose.onNodeWithText("Cambio de aceite").assertDoesNotExist()
        compose.onNodeWithTag("rf18.reintentar").performClick()
        compose.runOnIdle { assertEquals(1, reintentos) }
    }

    @Test fun sinVehiculosNoInventaSelectorNiTarjetas() {
        mostrar(EstadoHistorial(FaseHistorial.SIN_VEHICULOS))
        compose.onNodeWithText("No tienes vehículos registrados").assertExists()
        compose.onNodeWithTag("rf18.selector").assertDoesNotExist()
        compose.onNodeWithTag("rf18.registrar").assertIsNotEnabled()
    }

    @Test fun activoAbreRegistroConSuIdYVolverInvocaNavegacion() {
        var id: Long? = null; var salidas = 0
        mostrar(EstadoHistorial(FaseHistorial.LISTO, listOf(activo), activo),
            registrar = { id = it }, volver = { salidas++ })
        compose.onNodeWithTag("rf18.registrar").performClick()
        compose.onNodeWithTag("rf18.volver").performClick()
        compose.runOnIdle { assertEquals(10L, id); assertEquals(1, salidas) }
    }
    @Test fun sinClaveDeSesionNoConsultaRepositorios() {
        val consultas = java.util.concurrent.atomic.AtomicInteger()
        val repository = object : HistorialRepository {
            override suspend fun vehiculos(): List<VehiculoHistorial> { consultas.incrementAndGet(); return listOf(activo) }
            override suspend fun historial(idVehiculo: Long): ResultadoHistorial { consultas.incrementAndGet(); return ResultadoHistorial(activo, registros) }
        }
        compose.setContent { MyCarTheme {
            EntradaHistorialMantenimiento(repository, null, null, {}, {}, {}, registroDisponible = true)
        } }
        compose.onNodeWithText("Inicia sesión para consultar los mantenimientos de tus vehículos.").assertExists()
        compose.onNodeWithTag("rf18.registrar").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, consultas.get()) }
    }

    @Test fun irARF17YCancelarRegresaAlHistorialDelMismoVehiculoYRecarga() {
        val consultas = java.util.concurrent.atomic.AtomicInteger()
        val historial = object : HistorialRepository {
            override suspend fun vehiculos() = listOf(activo)
            override suspend fun historial(idVehiculo: Long): ResultadoHistorial {
                consultas.incrementAndGet()
                return ResultadoHistorial(activo, emptyList())
            }
        }
        val mantenimiento = object : MantenimientoRepository {
            override suspend fun contexto() = ContextoRf17Dto("2026-10-27", "America/Lima",
                listOf(VehiculoRf17Dto(10, "ABC123", "Toyota", "Corolla", true, 701, 47000,
                    "2026-10-27T15:00:00Z", null)),
                listOf(TipoRf17Dto(5, "MANTENIMIENTO_GENERAL", "Mantenimiento general")), true)
            override suspend fun lecturas(vehiculo: Long, fecha: String) = listOf(LecturaRf17Dto(701, 47000, "2026-10-27T15:00:00Z"))
            override suspend fun registrar(vehiculo: Long, solicitud: SolicitudRf17Dto): ResultadoRf17Dto = error("Esta prueba no guarda")
            override suspend fun consultar(idOperacion: String): EstadoRf17Dto = error("Esta prueba no consulta operaciones")
        }
        compose.setContent { MyCarTheme {
            FlujoHistorialMantenimiento({}, historialRepository = historial,
                mantenimientoRepository = mantenimiento, claveCuenta = "rf18-test-" + "navegacion")
        } }
        compose.waitUntil(10000) { consultas.get() >= 1 && compose.onAllNodesWithTag("rf18.contador").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("rf18.registrar").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Registrar mantenimiento").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("rf17.cancelar").performClick()
        compose.onNodeWithText("Descartar", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { consultas.get() >= 2 && compose.onAllNodesWithTag("rf18.contador").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ABC123 · Activo").assertExists()
    }
}
