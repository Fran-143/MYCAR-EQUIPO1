package com.mycar.app.features.alertas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.DatosAlerta
import com.mycar.app.features.alertas.viewmodel.AlertasViewModel
import com.mycar.app.features.alertas.viewmodel.FaseAlertas
import com.mycar.app.features.alertas.viewmodel.FiltroAlertas
import com.mycar.app.ui.theme.MyCarStatusPendiente
import com.mycar.app.ui.theme.MyCarStatusProgramada
import com.mycar.app.ui.theme.MyCarStatusProxima
import com.mycar.app.ui.theme.MyCarStatusRealizada
import com.mycar.app.ui.theme.MyCarStatusVencida

@Composable
fun AlertasRoute(
    repository: AlertasRepository,
    vehiculos: List<Pair<Long, String>>,
    idVehiculoInicial: Long?,
    onProgramar: (Long) -> Unit,
    recargaToken: Int = 0,
    reiniciarFiltroTodas: Boolean = false,
    onReinicioFiltroConsumido: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val vm: AlertasViewModel = viewModel(key = "rf23-lista") {
        AlertasViewModel(repository, vehiculos, idVehiculoInicial)
    }
    val estado by vm.estado.collectAsStateWithLifecycle()
    var detalleId by remember { mutableStateOf<Long?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && detalleId == null) vm.recargar()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(vehiculos, idVehiculoInicial) {
        val id = idVehiculoInicial ?: vehiculos.firstOrNull()?.first
        if (id != null) vm.seleccionar(id)
    }
    LaunchedEffect(recargaToken, reiniciarFiltroTodas) {
        if (recargaToken > 0) {
            if (reiniciarFiltroTodas) {
                vm.reiniciarFiltroTodasYRecargar()
                onReinicioFiltroConsumido()
            } else {
                vm.recargar()
            }
        }
    }
    val idDetalle = detalleId
    val idVehiculo = estado.vehiculoId
    if (idDetalle != null && idVehiculo != null) {
        DetalleAlertaRoute(
            repository = repository,
            idVehiculo = idVehiculo,
            idAlerta = idDetalle,
            onVolver = {
                detalleId = null
                vm.recargar()
            },
            modifier = modifier
        )
    } else {
        AlertasScreen(
            estado, vehiculos, vm::seleccionar, vm::cambiarFiltro, vm::recargar, onProgramar,
            onAtender = vm::atender, onAnular = vm::anular,
            onVerDetalle = { detalleId = it },
            modifier = modifier
        )
    }
}

@Composable
fun AlertasScreen(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoAlertas,
    vehiculos: List<Pair<Long, String>>,
    onSeleccionar: (Long) -> Unit,
    onFiltro: (FiltroAlertas) -> Unit,
    onRecargar: () -> Unit,
    onProgramar: (Long) -> Unit,
    onAtender: (Long, Int, String, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onAnular: (Long) -> Unit = {},
    onVerDetalle: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Alertas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer
            )
            val id = estado.vehiculoId
            if (id != null) {
                Button(
                    onClick = { onProgramar(id) },
                    enabled = estado.fase != FaseAlertas.CARGANDO,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary)
                ) { Text("+ Programar", fontWeight = FontWeight.SemiBold) }
            }
        }

        if (vehiculos.isEmpty()) {
            Text("No tienes vehículos activos para consultar alertas.")
            return@Column
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Vehículo seleccionado", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                if (vehiculos.size > 1) {
                    var expandido by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { expandido = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                vehiculos.firstOrNull { it.first == estado.vehiculoId }?.second ?: "Elegir vehículo",
                                modifier = Modifier.weight(1f)
                            )
                            Text("⌄")
                        }
                        DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
                            vehiculos.forEach { (id, etiqueta) ->
                                DropdownMenuItem(
                                    text = { Text(etiqueta) },
                                    onClick = { expandido = false; onSeleccionar(id) }
                                )
                            }
                        }
                    }
                } else {
                    Text(vehiculos.first().second, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FiltroAlertas.entries.forEach { f ->
                FilterChip(
                    selected = estado.filtro == f,
                    onClick = { onFiltro(f) },
                    enabled = estado.fase != FaseAlertas.CARGANDO,
                    label = { Text(f.etiqueta) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.onPrimaryContainer,
                        selectedLabelColor = colors.onPrimary
                    )
                )
            }
        }

        when (estado.fase) {
            FaseAlertas.CARGANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Actualizando alertas…")
            }
            FaseAlertas.ERROR -> {
                Text(estado.mensaje ?: "No se pudieron cargar las alertas.")
                OutlinedButton(onClick = onRecargar) { Text("Reintentar") }
            }
            FaseAlertas.VACIO -> Text(
                if (estado.filtro == FiltroAlertas.TODAS) "Aún no hay alertas para este vehículo."
                else "No hay alertas «${estado.filtro.etiqueta.lowercase()}» para este vehículo."
            )
            FaseAlertas.LISTO -> {
                if (estado.sincronizadas > 0) {
                    Text(
                        "Se actualizó el estado de ${estado.sincronizadas} alerta(s).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                estado.avisoAccion?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.tertiary)
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = true)
                ) {
                    items(estado.visibles, key = { it.id }) { a ->
                        TarjetaAlerta(
                            a,
                            ocupado = estado.ocupadoAccion,
                            onAtender = onAtender,
                            onAnular = onAnular,
                            onVerDetalle = onVerDetalle
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaAlerta(
    a: DatosAlerta,
    ocupado: Boolean = false,
    onAtender: (Long, Int, String, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onAnular: (Long) -> Unit = {},
    onVerDetalle: (Long) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val badge = colorEstado(a.estado)
    val atendible = a.estado in setOf("PROXIMA", "PENDIENTE", "VENCIDA")
    val anulable = a.estado in setOf("PROGRAMADA", "PROXIMA", "PENDIENTE", "VENCIDA")
    var dialogAtender by remember { mutableStateOf(false) }
    var dialogAnular by remember { mutableStateOf(false) }
    var kmTexto by remember { mutableStateOf(a.kmObjetivo?.toString().orEmpty()) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onVerDetalle(a.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    a.tipo.ifBlank { a.servicioOtro ?: "Servicio" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(50), color = badge.copy(alpha = 0.15f)) {
                    Text(
                        etiquetaEstado(a.estado).uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badge
                    )
                }
            }
            Text(etiquetaCriterio(a.criterio), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            a.fechaObjetivo?.let {
                Text("Fecha: $it", style = MaterialTheme.typography.bodyMedium)
            }
            a.kmObjetivo?.let {
                Text("Km objetivo: %,d km".format(it).replace(',', ' '), style = MaterialTheme.typography.bodyMedium)
            }
            a.servicioOtro?.takeIf { a.tipo.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            TextButton(onClick = { onVerDetalle(a.id) }) { Text("Ver detalle →") }
            if (atendible || anulable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (atendible) {
                        Button(
                            onClick = { dialogAtender = true },
                            enabled = !ocupado,
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Atender") }
                    }
                    if (anulable) {
                        OutlinedButton(
                            onClick = { dialogAnular = true },
                            enabled = !ocupado,
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Anular") }
                    }
                }
            }
        }
    }
    if (dialogAtender) {
        AlertDialog(
            onDismissRequest = { dialogAtender = false },
            title = { Text("Atender alerta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se registrará un mantenimiento nuevo y la alerta pasará a REALIZADA.")
                    OutlinedTextField(
                        value = kmTexto,
                        onValueChange = { kmTexto = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Km del servicio") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val km = kmTexto.toIntOrNull()
                    if (km != null && km > 0) {
                        dialogAtender = false
                        onAtender(a.id, km, java.time.LocalDate.now().toString(), null, null)
                    }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { dialogAtender = false }) { Text("Cancelar") } }
        )
    }
    if (dialogAnular) {
        AlertDialog(
            onDismissRequest = { dialogAnular = false },
            title = { Text("¿Anular alerta?") },
            text = { Text("La alerta quedará ANULADA. El mantenimiento histórico de referencia no se anula.") },
            confirmButton = {
                TextButton(onClick = { dialogAnular = false; onAnular(a.id) }) { Text("Anular") }
            },
            dismissButton = { TextButton(onClick = { dialogAnular = false }) { Text("Cancelar") } }
        )
    }
}

private fun colorEstado(estado: String): Color = when (estado) {
    "PROGRAMADA" -> MyCarStatusProgramada
    "PROXIMA" -> MyCarStatusProxima
    "PENDIENTE" -> MyCarStatusPendiente
    "VENCIDA" -> MyCarStatusVencida
    "REALIZADA" -> MyCarStatusRealizada
    else -> MyCarStatusRealizada
}

private fun etiquetaCriterio(criterio: String): String = when (criterio) {
    "POR_TIEMPO" -> "Por tiempo"
    "POR_KILOMETRAJE" -> "Por kilometraje"
    "AMBOS" -> "Por tiempo y kilometraje"
    else -> criterio
}

private fun etiquetaEstado(estado: String): String = when (estado) {
    "PROGRAMADA" -> "Programada"
    "PROXIMA" -> "Próxima"
    "PENDIENTE" -> "Pendiente"
    "VENCIDA" -> "Vencida"
    "REALIZADA" -> "Realizada"
    else -> estado
}
