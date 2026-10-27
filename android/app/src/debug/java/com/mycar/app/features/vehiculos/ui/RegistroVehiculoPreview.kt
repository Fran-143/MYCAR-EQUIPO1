package com.mycar.app.features.vehiculos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.mycar.app.features.vehiculos.data.*
import com.mycar.app.features.vehiculos.domain.*
import com.mycar.app.ui.theme.MyCarTheme

/** Datos exclusivos de diseño DEBUG: no son semillas, sesión ni repositorio de ejecución. */
@Preview(showBackground = true, widthDp = 400, heightDp = 900)
@Composable
fun RegistroVehiculoPreview() {
    val catalogos = remember { CatalogosRegistroDto(
        listOf(UsoRegistroDto("PARTICULAR", "Particular"), UsoRegistroDto("TAXI", "Taxi")),
        listOf(MotorRegistroDto(1, "COMBUSTION", "Combustión", false),
            MotorRegistroDto(2, "ELECTRICO", "Eléctrico", true), MotorRegistroDto(3, "OTRO", "Otro", false)),
        listOf(CombustibleRegistroDto(1, "GASOLINA", "Gasolina"), CombustibleRegistroDto(2, "OTRO", "Otro")),
        listOf(AceiteRegistroDto(1, "MINERAL", "Mineral"), AceiteRegistroDto(2, "OTRO", "Otro"))) }
    var estado by remember { mutableStateOf(EstadoRegistroVehiculo(fase = FaseRegistro.FORMULARIO, catalogos = catalogos)) }
    MyCarTheme {
        Column {
            Text("Vista de diseño · no guarda vehículos", style = MaterialTheme.typography.labelSmall)
            RegistroVehiculoScreen(estado,
                onCambiar = { campo, valor -> estado = estado.copy(formulario = estado.formulario.cambiar(campo, valor, catalogos), errores = estado.errores - campo, mensaje = null) },
                onGuardar = {
                    val errores = ValidadorFormularioRegistro.validar(estado.formulario, catalogos)
                    estado = estado.copy(errores = errores, mensaje = if (errores.isEmpty()) "Campos válidos en la vista de diseño." else "Revisa los campos señalados.")
                }, onRecargar = {}, onComprobar = {}, onReenviar = {}, onVolver = {},
                onIniciarSesion = {}, onVerVehiculos = {}, onIrInicio = {})
        }
    }
}
