package com.mycar.app.features.mantenimiento.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycar.app.features.mantenimiento.data.ResultadoRf17Dto
import com.mycar.app.features.mantenimiento.viewmodel.OfertaProgramarAlerta

@Composable
internal fun ResultadoMantenimientoScreen(
    r: ResultadoRf17Dto,
    modifier: Modifier,
    onVolver: () -> Unit,
    oferta: OfertaProgramarAlerta? = null,
    atencionDesdeAlerta: Boolean = false,
    onProgramarAlerta: (OfertaProgramarAlerta) -> Unit = {}
) {
    Column(modifier.fillMaxSize().testTag("rf17.resultado").verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text("✓", Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            if (atencionDesdeAlerta) "Alerta atendida" else "Mantenimiento registrado",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold
        )
        Text(
            if (atencionDesdeAlerta)
                "Se registró el mantenimiento y la alerta quedó REALIZADA."
            else
                "El servicio se guardó correctamente.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DatoResultado("Número de registro", r.idMantenimiento?.toString() ?: "—")
                DatoResultado("Fecha del servicio", r.fechaServicio?.let(::fechaServicioVisible) ?: "—")
                DatoResultado("Kilometraje del servicio", r.kmServicio?.let { "$it km" } ?: "—")
                DatoResultado("Costo total", r.costoTotal?.toPlainString() ?: "No informado")
                DatoResultado("Repuestos", r.cantidadRepuestos?.toString() ?: "—")
            }
        }
        if (oferta != null) {
            AvisoMantenimiento("¿Quieres programar ahora la alerta con la recomendación indicada?")
            Button(
                onClick = { onProgramarAlerta(oferta) },
                modifier = Modifier.fillMaxWidth().testTag("rf17.programar.alerta"),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Sí, programar alerta") }
            OutlinedButton(
                onClick = onVolver,
                modifier = Modifier.fillMaxWidth().testTag("rf17.resultado.volver")
            ) { Text("Ahora no") }
        } else {
            if (!atencionDesdeAlerta) {
                AvisoMantenimiento("Las recomendaciones se conservaron sin crear una alerta.")
            }
            Button(
                onClick = onVolver,
                modifier = Modifier.fillMaxWidth().testTag("rf17.resultado.volver")
            ) { Text(if (atencionDesdeAlerta) "Volver al detalle de la alerta" else "Volver") }
        }
    }
}

@Composable
private fun DatoResultado(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
