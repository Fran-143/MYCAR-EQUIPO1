package com.mycar.app.features.vehiculos.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mycar.app.features.vehiculos.data.*
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
    val catalogos = remember { CatalogosRegistroDto(
        listOf(UsoRegistroDto("PARTICULAR", "Particular"), UsoRegistroDto("TAXI", "Taxi")),
        listOf(MotorRegistroDto(1, "COMBUSTION", "Combustión", false),
            MotorRegistroDto(2, "ELECTRICO", "Eléctrico", true), MotorRegistroDto(3, "OTRO", "Otro", false)),
        listOf(CombustibleRegistroDto(1, "GASOLINA", "Gasolina"), CombustibleRegistroDto(2, "OTRO", "Otro")),
        listOf(AceiteRegistroDto(1, "MINERAL", "Mineral"), AceiteRegistroDto(2, "OTRO", "Otro"))) }
    var estado by remember { mutableStateOf(EstadoRegistroVehiculo(fase = FaseRegistro.FORMULARIO,
        catalogos = catalogos, formulario = inicial)) }
    MyCarTheme {
        Column {
            Text("Vista de diseño · no guarda vehículos", style = MaterialTheme.typography.labelSmall)
            RegistroVehiculoScreen(estado,
                onCambiar = { campo, valor -> estado = estado.copy(
                    formulario = estado.formulario.cambiar(campo, valor, catalogos),
                    errores = estado.errores - campo, mensaje = null) },
                onGuardar = {
                    val errores = ValidadorFormularioRegistro.validar(estado.formulario, catalogos)
                    estado = estado.copy(errores = errores, mensaje = if (errores.isEmpty())
                        "Campos válidos en la vista de diseño." else "Revisa los campos señalados.")
                }, onRecargar = {}, onComprobar = {}, onReenviar = {}, onVolver = {},
                onIniciarSesion = {}, onVerVehiculos = {}, onIrInicio = {}, modifier = Modifier.weight(1f))
        }
    }
}
