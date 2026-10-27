package com.mycar.app.features.vehiculos

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mycar.app.features.vehiculos.data.*
import com.mycar.app.features.vehiculos.domain.*
import com.mycar.app.features.vehiculos.ui.RegistroVehiculoScreen
import com.mycar.app.ui.theme.MyCarTheme
import org.junit.Rule
import org.junit.Test

/** Ejecutar con emulador: connectedDebugAndroidTest. No utiliza backend ni usuarios ficticios de producción. */
class RegistroVehiculoScreenTest {
    @get:Rule val compose = createComposeRule()
    private val catalogos = CatalogosRegistroDto(listOf(UsoRegistroDto("PARTICULAR", "Particular")),
        listOf(MotorRegistroDto(1, "COMBUSTION", "Combustión", false)),
        listOf(CombustibleRegistroDto(1, "GASOLINA", "Gasolina")), emptyList())
    private val valido = FormularioRegistroVehiculo(marca = "Toyota", modelo = "Corolla", usoVehiculoCodigo = "PARTICULAR",
        placa = "A1B234", anioFabricacion = "2020", color = "Gris", idTipoMotor = "1", idCombustible = "1", kilometrajeInicial = "100")
    private fun mostrar(formulario: FormularioRegistroVehiculo, errores: Map<String, String> = emptyMap()) {
        compose.setContent { MyCarTheme {
            RegistroVehiculoScreen(EstadoRegistroVehiculo(fase = FaseRegistro.FORMULARIO,
                catalogos = catalogos, formulario = formulario, errores = errores),
                onCambiar = { _, _ -> }, onGuardar = {}, onRecargar = {}, onComprobar = {}, onReenviar = {},
                onVolver = {}, onIniciarSesion = {}, onVerVehiculos = {}, onIrInicio = {})
        } }
    }
    @Test fun guardarEstaDeshabilitadoSiFaltanDatos() {
        mostrar(FormularioRegistroVehiculo())
        compose.onNodeWithText("Guardar vehículo").assertIsNotEnabled()
    }
    @Test fun guardarSeHabilitaConDatosValidos() {
        mostrar(valido)
        compose.onNodeWithText("Guardar vehículo").assertIsEnabled()
    }
    @Test fun seccionConErrorNoPuedeOcultarElCampo() {
        mostrar(valido, mapOf("numeroVin" to "VIN inválido"))
        compose.onNodeWithText("Identificación adicional (opcional)").performScrollTo().performClick()
        compose.onNodeWithText("VIN inválido").performScrollTo().assertIsDisplayed()
    }
}
