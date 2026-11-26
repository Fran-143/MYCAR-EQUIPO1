package com.mycar.app.features.mantenimiento.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mycar.app.features.mantenimiento.data.ResultadoRf17Dto
import com.mycar.app.features.mantenimiento.viewmodel.OfertaProgramarAlerta
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarStatusActivo
import com.mycar.app.ui.theme.MyCarTile

@Composable
internal fun ResultadoMantenimientoScreen(
    r: ResultadoRf17Dto,
    modifier: Modifier,
    onVolver: () -> Unit,
    oferta: OfertaProgramarAlerta? = null,
    atencionDesdeAlerta: Boolean = false,
    onProgramarAlerta: (OfertaProgramarAlerta) -> Unit = {}
) {
    Column(
        modifier
            .fillMaxSize()
            .testTag("rf17.resultado")
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Surface(shape = CircleShape, color = MyCarTile) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MyCarStatusActivo,
                modifier = Modifier.padding(18.dp).size(56.dp)
            )
        }
        Text(
            if (atencionDesdeAlerta) "Alerta atendida" else "Mantenimiento guardado",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MyCarNavy,
            textAlign = TextAlign.Center
        )
        Text(
            if (atencionDesdeAlerta)
                "Se registró el mantenimiento y la alerta quedó REALIZADA."
            else
                "El registro ha sido guardado correctamente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        OutlinedCard(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DatoResultado("Número de registro", r.idMantenimiento?.toString() ?: "—")
                DatoResultado("Fecha del servicio", r.fechaServicio?.let(::fechaServicioVisible) ?: "—")
                DatoResultado("Kilometraje del servicio", r.kmServicio?.let { "$it km" } ?: "—")
                DatoResultado("Costo total", r.costoTotal?.toPlainString() ?: "No informado")
                DatoResultado("Repuestos", r.cantidadRepuestos?.toString() ?: "—")
            }
        }
        if (oferta != null) {
            AlertDialog(
                onDismissRequest = onVolver,
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        "¿Convertir en alerta?",
                        fontWeight = FontWeight.Bold,
                        color = MyCarNavy
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "¿Quieres convertir este próximo mantenimiento en una alerta? " +
                                "Podrás recibir avisos cuando se acerque o quede pendiente."
                        )
                        Surface(shape = RoundedCornerShape(12.dp), color = MyCarTile) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    oferta.servicioOtro?.takeIf { it.isNotBlank() } ?: oferta.codigoTipo,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MyCarNavy
                                )
                                oferta.proximaFecha?.let { Text("Fecha: $it") }
                                oferta.proximoKm?.let { Text("Kilometraje: $it km") }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onProgramarAlerta(oferta) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy),
                        modifier = Modifier.testTag("rf17.programar.alerta")
                    ) { Text("Sí, programar alerta") }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = onVolver,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("rf17.resultado.volver")
                    ) { Text("No, solo guardar") }
                }
            )
        } else {
            Button(
                onClick = onVolver,
                modifier = Modifier.fillMaxWidth().testTag("rf17.resultado.volver"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
            ) {
                Text(
                    if (atencionDesdeAlerta) "Volver al detalle de la alerta" else "Ver historial"
                )
            }
            OutlinedButton(
                onClick = onVolver,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (atencionDesdeAlerta) "Cerrar" else "Volver al vehículo")
            }
        }
    }
}

@Composable
private fun DatoResultado(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MyCarNavy)
    }
}
