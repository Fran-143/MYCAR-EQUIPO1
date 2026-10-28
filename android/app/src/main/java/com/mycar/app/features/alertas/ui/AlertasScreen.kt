package com.mycar.app.features.alertas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.viewmodel.AlertasViewModel
import com.mycar.app.features.alertas.viewmodel.FaseAlertas

@Composable
fun AlertasRoute(
    repository: AlertasRepository,
    vehiculos: List<Pair<Long, String>>,
    idVehiculoInicial: Long?,
    onProgramar: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: AlertasViewModel = viewModel(key = "rf23-${vehiculos.hashCode()}-$idVehiculoInicial") {
        AlertasViewModel(repository, vehiculos, idVehiculoInicial)
    }
    val estado by vm.estado.collectAsStateWithLifecycle()
    AlertasScreen(estado, vehiculos, vm::seleccionar, vm::recargar, onProgramar, modifier)
}

@Composable
fun AlertasScreen(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoAlertas,
    vehiculos: List<Pair<Long, String>>,
    onSeleccionar: (Long) -> Unit,
    onRecargar: () -> Unit,
    onProgramar: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mis alertas", style = MaterialTheme.typography.headlineSmall)
        Text("RF23 sincroniza estados antes de mostrar la lista.", style = MaterialTheme.typography.bodySmall)
        if (vehiculos.size > 1) {
            var expandido by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { expandido = true }, modifier = Modifier.fillMaxWidth()) {
                Text(vehiculos.firstOrNull { it.first == estado.vehiculoId }?.second ?: "Elegir vehículo")
            }
            DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
                vehiculos.forEach { (id, etiqueta) ->
                    DropdownMenuItem(text = { Text(etiqueta) }, onClick = { expandido = false; onSeleccionar(id) })
                }
            }
        }
        when (estado.fase) {
            FaseAlertas.CARGANDO -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Sincronizando…") }
            FaseAlertas.ERROR -> {
                Text(estado.mensaje ?: "Error")
                OutlinedButton(onClick = onRecargar) { Text("Reintentar") }
            }
            FaseAlertas.VACIO -> Text("No hay alertas para este vehículo.")
            FaseAlertas.LISTO -> {
                if (estado.sincronizadas > 0) {
                    Text("Se actualizaron ${estado.sincronizadas} alerta(s).", style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
                    items(estado.alertas, key = { it.id }) { a ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(a.tipo, style = MaterialTheme.typography.titleMedium)
                                Text("Estado: ${a.estado} · ${a.criterio}")
                                a.fechaObjetivo?.let { Text("Objetivo fecha: $it", style = MaterialTheme.typography.bodySmall) }
                                a.kmObjetivo?.let { Text("Objetivo km: $it", style = MaterialTheme.typography.bodySmall) }
                                a.servicioOtro?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        }
        estado.vehiculoId?.let { id ->
            Button(onClick = { onProgramar(id) }, modifier = Modifier.fillMaxWidth()) {
                Text("Programar alerta (RF22)")
            }
        }
    }
}
