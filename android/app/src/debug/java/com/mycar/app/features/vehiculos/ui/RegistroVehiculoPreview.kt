package com.mycar.app.features.vehiculos.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mycar.app.features.vehiculos.domain.*
import com.mycar.app.ui.theme.MyCarTheme

/** Catálogos de diseño DEBUG; no crea sesión ni guarda datos en la BD. */
@Preview(name = "Registro · 390 dp", showBackground = true, widthDp = 390, heightDp = 850)
@Preview(name = "Registro · pantalla pequeña", showBackground = true, widthDp = 320, heightDp = 720)
@Preview(name = "Registro · letra grande", showBackground = true, widthDp = 390, heightDp = 850, fontScale = 1.3f)
@Preview(name = "Registro · oscuro", showBackground = true, widthDp = 390, heightDp = 850,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RegistroVehiculoPreview() = VistaRegistroPrueba(FormularioRegistroVehiculo())

@Preview(name = "Registro · datos de ejemplo", showBackground = true, widthDp = 390, heightDp = 850)
@Composable
fun RegistroVehiculoConDatosPreview() = VistaRegistroPrueba(FormularioRegistroVehiculo(
    marca = "Toyota", modelo = "Corolla", usoVehiculoCodigo = "PARTICULAR",
    placa = "A1B234", anioFabricacion = "2020", color = "Gris", idTipoMotor = "3",
    tipoMotorOtro = "Híbrido (HEV)", idCombustible = "2", combustibleOtro = "Gasolina premium",
    idTipoAceite = "2", tipoAceiteOtro = "5W-30 sintético", kilometrajeInicial = "58240"
))

@Composable
private fun VistaRegistroPrueba(inicial: FormularioRegistroVehiculo) {
    MyCarTheme { RegistroVehiculoDemo(inicial = inicial) }
}
