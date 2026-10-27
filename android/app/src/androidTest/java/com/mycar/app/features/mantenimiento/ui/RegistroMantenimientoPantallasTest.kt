package com.mycar.app.features.mantenimiento.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mycar.app.features.mantenimiento.data.*
import com.mycar.app.features.mantenimiento.domain.*
import com.mycar.app.features.mantenimiento.viewmodel.MantenimientoUiState
import com.mycar.app.ui.theme.MyCarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RegistroMantenimientoPantallasTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sinSesionMantieneElGuardadoBloqueado() {
        mostrar(MantenimientoUiState(cargando = false, requiereSesion = true), AccionesPantallaDePrueba())
        compose.onNodeWithText("Inicia sesión para elegir tu vehículo y registrar un mantenimiento.").assertIsDisplayed()
        compose.onNodeWithTag("rf17.guardar").assertIsNotEnabled()
        compose.onNodeWithTag("rf17.tipo").performScrollTo().assertIsNotEnabled()
    }

    @Test fun seleccionarOtroMuestraElCampoEspecificoYConservaLoEscrito() {
        var estado by mutableStateOf(estadoDisponible())
        val acciones = object : AccionesPantallaDePrueba() {
            override fun actualizar(cambio: (BorradorMantenimiento) -> BorradorMantenimiento) {
                estado = estado.copy(borrador = cambio(estado.borrador))
            }
        }
        compose.setContent {
            MyCarTheme(dynamicColor = false) {
                FormularioRegistroMantenimiento(estado, acciones, Modifier, {}, {}, null, null)
            }
        }
        compose.onNodeWithTag("rf17.tipo").performScrollTo().performClick()
        compose.onNodeWithText("Otro").performClick()
        compose.onNodeWithTag("rf17.servicioOtro").performScrollTo().performTextInput("Afinamiento")
        compose.onNodeWithTag("rf17.servicioOtro").assertTextContains("Afinamiento")
        compose.runOnIdle { assertEquals("Afinamiento", estado.borrador.servicioOtro) }
    }

    @Test fun resultadoInciertoBloqueaGuardarYPermiteSolicitarVerificacion() {
        var verificaciones = 0
        val estado = estadoDisponible().let { it.copy(incierto = true, borrador = it.borrador.copy(envioPendiente = true)) }
        mostrar(estado, object : AccionesPantallaDePrueba() {
            override fun consultarOperacion() { verificaciones++ }
        })
        compose.onNodeWithTag("rf17.guardar").assertIsNotEnabled()
        compose.onNodeWithTag("rf17.verificar").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, verificaciones) }
    }

    @Test fun volverPideConfirmacionYSeguirEditandoConservaElBorrador() {
        var estado by mutableStateOf(estadoDisponible())
        var cierres = 0
        val acciones = object : AccionesPantallaDePrueba() {
            override fun solicitarSalir() { estado = estado.copy(confirmarSalida = true) }
            override fun seguirEditando() { estado = estado.copy(confirmarSalida = false) }
        }
        compose.setContent {
            MyCarTheme(dynamicColor = false) {
                FormularioRegistroMantenimiento(estado, acciones, Modifier, { cierres++ }, {}, null, null)
            }
        }
        compose.onNodeWithTag("rf17.volver").performClick()
        compose.onNodeWithText("¿Descartar el borrador?").assertIsDisplayed()
        compose.onNodeWithText("Seguir editando").performClick()
        compose.onNodeWithText("¿Descartar el borrador?").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("180.00", estado.borrador.costo)
            assertEquals(0, cierres)
        }
    }

    private fun mostrar(estado: MantenimientoUiState, acciones: AccionesMantenimiento) {
        compose.setContent {
            MyCarTheme(dynamicColor = false) {
                FormularioRegistroMantenimiento(estado, acciones, Modifier, {}, {}, null, null)
            }
        }
    }

    private fun estadoDisponible() = MantenimientoUiState(cargando = false,
        contexto = ContextoRf17Dto("2026-10-27", "America/Lima",
            listOf(VehiculoRf17Dto(1L, "A1B234", "Toyota", "Yaris", true, 701L, 21000, "2026-10-26T15:00:00Z", "5W-30")),
            listOf(TipoRf17Dto(1L, "MANTENIMIENTO_GENERAL", "Mantenimiento general"), TipoRf17Dto(2L, "OTRO", "Otro")), true),
        borrador = BorradorMantenimiento(idVehiculo = 1L, codigoTipo = "MANTENIMIENTO_GENERAL", fecha = "2026-10-27",
            km = "21000", lectura = 701L, costo = "180.00"))
}

private open class AccionesPantallaDePrueba : AccionesMantenimiento {
    override fun actualizar(cambio: (BorradorMantenimiento) -> BorradorMantenimiento) {}
    override fun recargar() {}
    override fun fecha(fecha: String) {}
    override fun origen(origen: String) {}
    override fun lectura(id: Long) {}
    override fun solicitarVehiculo(id: Long) {}
    override fun confirmarVehiculo() {}
    override fun cancelarVehiculo() {}
    override fun editarRepuesto(id: String?) {}
    override fun cambiarEditor(cambio: (RepuestoBorrador) -> RepuestoBorrador) {}
    override fun cerrarEditor() {}
    override fun confirmarRepuesto() {}
    override fun quitarRepuesto(id: String) {}
    override fun guardar() {}
    override fun reintentarMismaOperacion() {}
    override fun consultarOperacion() {}
    override fun solicitarSalir() {}
    override fun seguirEditando() {}
    override fun descartar() {}
}
