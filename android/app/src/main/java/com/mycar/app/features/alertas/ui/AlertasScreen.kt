package com.mycar.app.features.alertas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.viewmodel.AlertasViewModel
import com.mycar.app.features.alertas.viewmodel.FaseAlertas
import com.mycar.app.features.alertas.viewmodel.FiltroAlertas

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
    AlertasScreen(
        estado, vehiculos, vm::seleccionar, vm::cambiarFiltro, vm::recargar, onProgramar, modifier
    )
}

@Composable
fun AlertasScreen(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoAlertas,
    vehiculos: List<Pair<Long, String>>,
    onSeleccionar: (Long) -> Unit,
    onFiltro: (FiltroAlertas) -> Unit,
    onRecargar: () -> Unit,
    onProgramar: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Mis alertas", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Consulta y programa recordatorios de mantenimiento para tu vehículo.",
            style = MaterialTheme.typography.bodySmall
        )
        if (vehiculos.isEmpty()) {
            Text("No tienes vehículos activos para consultar alertas.")
            return@Column
        }
        if (vehiculos.size > 1) {
            var expandido by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { expandido = true }, modifier = Modifier.fillMaxWidth()) {
                Text(vehiculos.firstOrNull { it.first == estado.vehiculoId }?.second ?: "Elegir vehículo")
            }
            DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
                vehiculos.forEach { (id, etiqueta) ->
                    DropdownMenuItem(
                        text = { Text(etiqueta) },
                        onClick = { expandido = false; onSeleccionar(id) }
                    )
                }
            }
        } else {
            Text(vehiculos.first().second, style = MaterialTheme.typography.titleMedium)
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
                    label = { Text(f.etiqueta) }
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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(estado.visibles, key = { it.id }) { a ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(a.tipo, style = MaterialTheme.typography.titleMedium)
                                Text("Estado: ${etiquetaEstado(a.estado)}")
                                Text(etiquetaCriterio(a.criterio), style = MaterialTheme.typography.bodySmall)
                                a.fechaObjetivo?.let {
                                    Text("Fecha objetivo: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                a.kmObjetivo?.let {
                                    Text("Kilometraje objetivo: $it km", style = MaterialTheme.typography.bodySmall)
                                }
                                a.servicioOtro?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        val id = estado.vehiculoId
        if (id != null) {
            Button(
                onClick = { onProgramar(id) },
                enabled = estado.fase != FaseAlertas.CARGANDO,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Programar alerta")
            }
        }
    }
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
