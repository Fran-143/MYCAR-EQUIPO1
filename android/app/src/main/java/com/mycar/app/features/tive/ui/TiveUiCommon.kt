package com.mycar.app.features.tive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.features.tive.data.TiveConsultaResponse
import com.mycar.app.features.tive.data.TiveDto
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarTeal

@Composable
fun TiveTarjetaVehiculo(
    marca: String?,
    modelo: String?,
    placa: String?,
    numeroVin: String? = null,
    anio: Int? = null,
    color: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Vehículo", fontWeight = FontWeight.Bold, color = MyCarNavy)
            val titulo = listOfNotNull(marca?.takeIf { it.isNotBlank() }, modelo?.takeIf { it.isNotBlank() })
                .joinToString(" ")
                .ifBlank { "Vehículo" }
            Text(titulo, fontWeight = FontWeight.SemiBold, color = MyCarTeal)
            placa?.takeIf { it.isNotBlank() }?.let { FilaTive("Placa", it) }
            anio?.let { FilaTive("Año", it.toString()) }
            color?.takeIf { it.isNotBlank() }?.let { FilaTive("Color", it) }
            numeroVin?.takeIf { it.isNotBlank() }?.let { FilaTive("VIN", it) }
        }
    }
}

@Composable
fun TiveTarjetaVehiculoDesdeConsulta(consulta: TiveConsultaResponse?, tive: TiveDto? = null, modifier: Modifier = Modifier) {
    TiveTarjetaVehiculo(
        marca = consulta?.marca,
        modelo = consulta?.modelo,
        placa = consulta?.placa ?: tive?.placa,
        numeroVin = consulta?.numeroVin ?: tive?.numeroVin,
        anio = consulta?.anioFabricacion,
        color = consulta?.color,
        modifier = modifier
    )
}

@Composable
fun TiveTarjetaDatos(tive: TiveDto, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Información de la TIVE", fontWeight = FontWeight.Bold, color = MyCarNavy)
            FilaTive("N.º de título", tive.numeroTitulo)
            FilaTive("Código de verificación", tive.codigoVerificacion)
            FilaTive("Fecha de título", SoatFormulario.formatearFecha(tive.fechaTitulo))
        }
    }
}

@Composable
fun FilaTive(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, color = MyCarMuted, modifier = Modifier.weight(1f))
        Text(
            valor,
            fontWeight = FontWeight.Medium,
            color = MyCarNavy,
            modifier = Modifier.weight(1.2f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
