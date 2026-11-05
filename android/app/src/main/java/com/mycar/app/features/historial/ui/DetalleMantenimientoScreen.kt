package com.mycar.app.features.historial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.historial.domain.HistorialRepository
import com.mycar.app.features.historial.viewmodel.DetalleMantenimientoViewModel
import com.mycar.app.features.historial.viewmodel.EstadoDetalleMantenimiento
import com.mycar.app.features.historial.viewmodel.FaseDetalle
import java.time.format.DateTimeFormatter

private val Fondo = Color(0xFFF0F5FA)
private val Azul = Color(0xFF12285F)
private val Celeste = Color(0xFF007E98)

@Composable
fun DetalleMantenimientoRoute(
    repository: HistorialRepository,
    idVehiculo: Long,
    idMantenimiento: Long,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val factory = remember(repository, idVehiculo, idMantenimiento) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DetalleMantenimientoViewModel(repository, idVehiculo, idMantenimiento) as T
            }
        }
    }
    val vm: DetalleMantenimientoViewModel = viewModel(
        key = "rf19:$idVehiculo:$idMantenimiento",
        factory = factory
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    DetalleMantenimientoScreen(
        estado = estado,
        onVolver = onVolver,
        onReintentar = vm::cargar,
        onEditar = vm::empezarEdicion,
        onCancelarEdicion = vm::cancelarEdicion,
        onGuardar = vm::guardar,
        onAnular = vm::anular,
        onCosto = vm::actualizarCosto,
        onObservaciones = vm::actualizarObservaciones,
        onTaller = vm::actualizarTaller,
        modifier = modifier
    )
}

@Composable
fun DetalleMantenimientoScreen(
    estado: EstadoDetalleMantenimiento,
    onVolver: () -> Unit,
    onReintentar: () -> Unit,
    onEditar: () -> Unit,
    onCancelarEdicion: () -> Unit,
    onGuardar: () -> Unit,
    onAnular: () -> Unit,
    onCosto: (String) -> Unit,
    onObservaciones: (String) -> Unit,
    onTaller: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmarAnular by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(Fondo).testTag("rf19.pantalla")) {
        Surface(color = Color.White) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onVolver, modifier = Modifier.testTag("rf19.volver")) { Text("Volver") }
                Text(
                    "Detalle",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Azul
                )
            }
        }
        when (estado.fase) {
            FaseDetalle.CARGANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = Celeste)
                Text("Cargando detalle…", Modifier.padding(16.dp), color = Azul)
            }
            FaseDetalle.ERROR -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(estado.mensaje ?: "No se pudo cargar el detalle", color = Azul)
                Button(onClick = onReintentar, modifier = Modifier.testTag("rf19.reintentar")) { Text("Reintentar") }
            }
            FaseDetalle.ANULADO -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(estado.mensaje ?: "Registro anulado", color = Azul, fontWeight = FontWeight.SemiBold)
                Button(onClick = onVolver) { Text("Volver al historial") }
            }
            FaseDetalle.LISTO, FaseDetalle.EDITANDO -> {
                val d = estado.detalle ?: return
                val editando = estado.fase == FaseDetalle.EDITANDO
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(d.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Azul)
                    Text(
                        "${d.fecha.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))} · ${d.kmServicio} km",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Estado: ${d.estado}", fontWeight = FontWeight.SemiBold, color = Azul)
                    if (d.bloqueadoPorAlertaActiva) {
                        Text(
                            "Hay una alerta activa que usa este mantenimiento como referencia. Debes atenderla o anularla antes de anular el registro.",
                            color = Color(0xFF943535),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (editando) {
                        OutlinedTextField(
                            value = estado.costoTexto, onValueChange = onCosto,
                            label = { Text("Costo") }, modifier = Modifier.fillMaxWidth().testTag("rf20.costo")
                        )
                        OutlinedTextField(
                            value = estado.observaciones, onValueChange = onObservaciones,
                            label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth().testTag("rf20.obs")
                        )
                        OutlinedTextField(
                            value = estado.taller, onValueChange = onTaller,
                            label = { Text("Taller (vacío = Sin taller)") },
                            modifier = Modifier.fillMaxWidth().testTag("rf20.taller")
                        )
                    } else {
                        Text(if (d.costo == null) "Costo: No registrado" else "Costo: S/ ${d.costo.toPlainString()}")
                        Text("Observaciones: ${d.observaciones ?: "—"}")
                        Text("Taller: ${d.taller ?: "Sin taller"}")
                    }
                    if (d.repuestos.isNotEmpty()) {
                        Text("Repuestos", fontWeight = FontWeight.SemiBold, color = Azul)
                        d.repuestos.forEach { r ->
                            Text("• ${r.nombre} × ${r.cantidad}")
                        }
                    }
                    estado.mensaje?.let { Text(it, color = Celeste) }
                    if (editando) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onCancelarEdicion, enabled = !estado.ocupado) { Text("Cancelar") }
                            Button(
                                onClick = onGuardar,
                                enabled = !estado.ocupado,
                                modifier = Modifier.testTag("rf20.guardar"),
                                colors = ButtonDefaults.buttonColors(containerColor = Celeste)
                            ) { Text("Guardar") }
                        }
                    } else {
                        if (d.editable) {
                            Button(
                                onClick = onEditar,
                                modifier = Modifier.fillMaxWidth().testTag("rf19.editar"),
                                colors = ButtonDefaults.buttonColors(containerColor = Celeste)
                            ) { Text("Editar mantenimiento") }
                        }
                        if (d.anulable) {
                            OutlinedButton(
                                onClick = { confirmarAnular = true },
                                enabled = !estado.ocupado,
                                modifier = Modifier.fillMaxWidth().testTag("rf19.anular")
                            ) { Text("Anular registro") }
                        } else if (d.estado == "REALIZADO" && d.bloqueadoPorAlertaActiva) {
                            Text("Anulación bloqueada por alerta activa", color = Color(0xFF943535))
                        }
                    }
                }
            }
        }
    }
    if (confirmarAnular) {
        AlertDialog(
            onDismissRequest = { confirmarAnular = false },
            title = { Text("¿Anular este mantenimiento?") },
            text = { Text("El registro quedará ANULADO. No se borran kilometraje ni repuestos.") },
            confirmButton = {
                TextButton(onClick = { confirmarAnular = false; onAnular() }, modifier = Modifier.testTag("rf21.confirmar")) {
                    Text("Anular")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarAnular = false }, modifier = Modifier.testTag("rf21.cancelar")) {
                    Text("Cancelar")
                }
            }
        )
    }
}
