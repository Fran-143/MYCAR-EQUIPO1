package com.mycar.app.features.soat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.features.soat.domain.SoatRepository
import com.mycar.app.features.soat.viewmodel.RecordatorioSoatViewModel
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarTeal

@Composable
fun RecordatorioSoatRoute(
    claveCuenta: String,
    idVehiculo: Long,
    idSoat: Long,
    recordatorioInicial: Boolean,
    repository: SoatRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onGuardado: () -> Unit,
    fechaVencimiento: String? = null,
    modifier: Modifier = Modifier
) {
    val vm: RecordatorioSoatViewModel = viewModel(
        key = "rf32-$claveCuenta-$idVehiculo-$idSoat",
        factory = remember(repository, idVehiculo, idSoat, recordatorioInicial) {
            RecordatorioSoatViewModel.factory(idVehiculo, idSoat, repository, recordatorioInicial)
        }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    RecordatorioSoatScreen(
        estado = estado,
        fechaVencimiento = fechaVencimiento,
        onVolver = onVolver,
        onIniciarSesion = onIniciarSesion,
        onCambio = vm::alternar,
        onExitoVolver = onGuardado,
        modifier = modifier
    )
}

@Composable
fun RecordatorioSoatScreen(
    estado: RecordatorioSoatViewModel.Estado,
    fechaVencimiento: String?,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onCambio: (Boolean) -> Unit,
    onExitoVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendienteActivar by remember { mutableStateOf(false) }
    val primerAviso = SoatFormulario.fechaPrimerAviso(fechaVencimiento)

    // El switch queda usable tras activar/desactivar (antes el éxito bloqueaba volver a prenderlo).
    SoatScaffold("Recordatorio del SOAT", onVolver, estado.ocupado, modifier) {
        if (estado.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
        Text(
            "Te avisaremos cuando falten 30 días para el vencimiento del SOAT.",
            color = MyCarMuted,
            fontWeight = FontWeight.Medium
        )
        if (estado.exito) {
            Text(
                if (estado.activo) "Recordatorio activado" else "Recordatorio desactivado",
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.SemiBold
            )
        }
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fechaVencimiento?.let {
                    FilaSoat("Vencimiento actual", SoatFormulario.formatearFecha(it))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Estado del recordatorio", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
                        Text(
                            if (estado.activo) "Activado" else "Desactivado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MyCarMuted
                        )
                    }
                    Switch(
                        checked = estado.activo,
                        onCheckedChange = { activar ->
                            if (activar && !estado.activo) pendienteActivar = true
                            else if (!activar && estado.activo) onCambio(false)
                        },
                        enabled = !estado.ocupado
                    )
                }
                if (estado.activo && primerAviso != null) {
                    Text(
                        "Próximo aviso: $primerAviso (30 días antes).",
                        color = MyCarNavy,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    "Puedes activar o desactivar el recordatorio cuando quieras.",
                    color = MyCarMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (estado.activo) {
            TextButton(onClick = { onCambio(false) }, enabled = !estado.ocupado) {
                Text("Desactivar recordatorio", color = Color(0xFFC62828))
            }
        }
        estado.mensaje?.let { Text(it, color = Color(0xFFC62828)) }
        if (estado.sesion) SoatBotonPrincipal("Iniciar sesión", onIniciarSesion)
        SoatBotonSecundario("Volver", onExitoVolver, enabled = !estado.ocupado)
    }

    if (pendienteActivar) {
        AlertDialog(
            onDismissRequest = { pendienteActivar = false },
            title = { Text("Confirmar activación", fontWeight = FontWeight.Bold, color = MyCarNavy) },
            text = {
                Text(
                    "Se activará el recordatorio del vencimiento del SOAT. Recibirás una alerta en la app y una notificación en tu dispositivo 30 días antes."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendienteActivar = false
                    onCambio(true)
                }) { Text("Activar") }
            },
            dismissButton = {
                TextButton(onClick = { pendienteActivar = false }) { Text("Cancelar") }
            }
        )
    }
}
