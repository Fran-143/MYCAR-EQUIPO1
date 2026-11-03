package com.mycar.app.features.vehiculos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mycar.app.features.vehiculos.data.DetalleVehiculoDto
import com.mycar.app.features.vehiculos.ui.DetalleVehiculoScreen
import com.mycar.app.features.vehiculos.viewmodel.*
import com.mycar.app.ui.theme.MyCarTheme
import org.junit.Rule
import org.junit.Test

/** Pantalla RF10 sin backend; ejecutar con connectedDebugAndroidTest. */
class DetalleVehiculoScreenTest {
    @get:Rule val compose = createComposeRule()
    private val dto = DetalleVehiculoDto(
        1, "A1B456", "Toyota", "Corolla", 2020, "Particular", "Gris",
        "Combustión", "Gasolina", "ACTIVO", true, 58240, "2020-06-01T12:00:00Z",
        null, null, null, "5W-30", false
    )

    @Test fun muestraCamposDocumentados() {
        compose.setContent {
            MyCarTheme {
                DetalleVehiculoScreen(
                    EstadoDetalleVehiculo(1, FaseDetalle.LISTO, dto),
                    onRecargar = {}, onVolver = {}, onIniciarSesion = {}
                )
            }
        }
        compose.onNodeWithText("A1B456").assertIsDisplayed()
        compose.onNodeWithText("Información general").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Identificación adicional").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Motor y mantenimiento").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("58 240 km").assertDoesNotExist()
        compose.onNodeWithText("58240 km").performScrollTo().assertIsDisplayed()
    }

    @Test fun errorPermiteReintentar() {
        compose.setContent {
            MyCarTheme {
                DetalleVehiculoScreen(
                    EstadoDetalleVehiculo(1, FaseDetalle.ERROR, mensaje = "Fallo"),
                    onRecargar = {}, onVolver = {}, onIniciarSesion = {}
                )
            }
        }
        compose.onNodeWithText("Reintentar").assertIsDisplayed()
    }

    @Test fun noDisponibleSinReintentar() {
        compose.setContent {
            MyCarTheme {
                DetalleVehiculoScreen(
                    EstadoDetalleVehiculo(1, FaseDetalle.NO_DISPONIBLE, mensaje = "No disponible"),
                    onRecargar = {}, onVolver = {}, onIniciarSesion = {}
                )
            }
        }
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
    }
}
