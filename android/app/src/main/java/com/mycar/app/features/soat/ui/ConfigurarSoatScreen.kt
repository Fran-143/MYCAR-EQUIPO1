package com.mycar.app.features.soat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy

@Composable
fun ConfigurarSoatScreen(
    onVolver: () -> Unit,
    onCorregir: () -> Unit,
    onRenovar: () -> Unit,
    onRecordatorio: () -> Unit,
    recordatorioActivo: Boolean?,
    fechaVencimiento: String? = null,
    modifier: Modifier = Modifier
) {
    val puedeRenovar = SoatFormulario.puedeRenovar(fechaVencimiento)
    SoatScaffold("Configurar SOAT", onVolver, modifier = modifier) {
        Text(
            "Gestiona la vigencia documental del SOAT.",
            color = MyCarMuted,
            fontWeight = FontWeight.Medium
        )
        recordatorioActivo?.let {
            Text(
                if (it) "Recordatorio: Activo" else "Recordatorio: Inactivo",
                color = MyCarNavy,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OpcionConfigurar(
                titulo = "Editar SOAT",
                detalle = "Actualiza los datos del SOAT y reemplaza el documento",
                onClick = onCorregir
            )
            OpcionConfigurar(
                titulo = "Renovar SOAT",
                detalle = if (puedeRenovar) {
                    "Registra un nuevo SOAT y actualiza su vigencia"
                } else {
                    "Disponible a partir del vencimiento (${SoatFormulario.formatearFecha(fechaVencimiento)})"
                },
                onClick = onRenovar,
                habilitado = puedeRenovar
            )
            if (!puedeRenovar && !fechaVencimiento.isNullOrBlank()) {
                Text(
                    SoatFormulario.MSG_RENOVAR_TEMPRANO,
                    color = Color(0xFFC62828),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OpcionConfigurar(
                titulo = "Configurar recordatorio",
                detalle = "Activa o ajusta el aviso de vencimiento del SOAT",
                onClick = onRecordatorio
            )
        }
    }
}

@Composable
private fun OpcionConfigurar(
    titulo: String,
    detalle: String,
    onClick: () -> Unit,
    habilitado: Boolean = true
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = habilitado, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (habilitado) Color.White else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (habilitado) 1.dp else 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    titulo,
                    fontWeight = FontWeight.Bold,
                    color = if (habilitado) MyCarNavy else MyCarMuted
                )
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MyCarMuted)
            }
            Text("›", color = MyCarMuted, style = MaterialTheme.typography.titleLarge)
        }
    }
}
