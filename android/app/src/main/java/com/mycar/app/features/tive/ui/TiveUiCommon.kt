package com.mycar.app.features.tive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.features.tive.data.TiveConsultaResponse
import com.mycar.app.features.tive.data.TiveDto
import com.mycar.app.ui.theme.MyCarBlue
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarTeal

fun enmascararDatoTive(valor: String?): String {
    val v = valor?.trim().orEmpty()
    if (v.isEmpty()) return ""
    if (v.length <= 4) return "••••"
    return "••••" + v.takeLast(4)
}

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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(40.dp).background(MyCarTeal.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.DirectionsCar, null, tint = MyCarTeal, modifier = Modifier.size(22.dp))
                }
                Text("Vehículo", fontWeight = FontWeight.Bold, color = MyCarNavy)
            }
            val titulo = listOfNotNull(marca?.takeIf { it.isNotBlank() }, modelo?.takeIf { it.isNotBlank() })
                .joinToString(" ")
                .ifBlank { "Vehículo" }
            Text(titulo, fontWeight = FontWeight.SemiBold, color = MyCarTeal, style = MaterialTheme.typography.titleMedium)
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
    var tituloVisible by remember { mutableStateOf(false) }
    var codigoVisible by remember { mutableStateOf(false) }
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(40.dp).background(MyCarBlue.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Description, null, tint = MyCarBlue, modifier = Modifier.size(22.dp))
                }
                Text("Información de la TIVE", fontWeight = FontWeight.Bold, color = MyCarNavy)
            }
            FilaTiveSensibles(
                icono = Icons.Outlined.Badge,
                etiqueta = "N.º de título",
                valorCompleto = tive.numeroTitulo,
                valorEnmascarado = enmascararDatoTive(tive.numeroTitulo),
                visible = tituloVisible,
                onToggle = { tituloVisible = !tituloVisible }
            )
            FilaTiveSensibles(
                icono = Icons.Outlined.Verified,
                etiqueta = "Código de verificación",
                valorCompleto = tive.codigoVerificacion,
                valorEnmascarado = enmascararDatoTive(tive.codigoVerificacion),
                visible = codigoVisible,
                onToggle = { codigoVisible = !codigoVisible }
            )
            FilaTiveConIcono(
                icono = Icons.Outlined.CalendarMonth,
                etiqueta = "Fecha de título",
                valor = SoatFormulario.formatearFecha(tive.fechaTitulo)
            )
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

@Composable
fun FilaTiveConIcono(icono: ImageVector, etiqueta: String, valor: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icono, null, tint = MyCarMuted, modifier = Modifier.size(20.dp))
        Text(etiqueta, color = MyCarMuted, modifier = Modifier.weight(1f))
        Text(valor, fontWeight = FontWeight.Medium, color = MyCarNavy)
    }
}

@Composable
fun FilaTiveSensibles(
    icono: ImageVector,
    etiqueta: String,
    valorCompleto: String,
    valorEnmascarado: String,
    visible: Boolean,
    onToggle: () -> Unit
) {
    // Etiqueta arriba (una línea) + valor abajo: evita wrap letra-a-letra con códigos largos.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icono,
            null,
            tint = MyCarMuted,
            modifier = Modifier.padding(top = 2.dp).size(20.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                etiqueta,
                color = MyCarMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                if (visible) valorCompleto else valorEnmascarado,
                fontWeight = FontWeight.Medium,
                color = MyCarNavy,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (visible) "Ocultar" else "Mostrar",
                tint = MyCarBlue
            )
        }
    }
}
