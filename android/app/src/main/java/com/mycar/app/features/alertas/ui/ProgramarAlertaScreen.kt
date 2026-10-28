package com.mycar.app.features.alertas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.viewmodel.BorradorAlerta
import com.mycar.app.features.alertas.viewmodel.ProgramarAlertaViewModel

@Composable
fun ProgramarAlertaRoute(
    repository: AlertasRepository,
    idVehiculo: Long,
    borradorInicial: BorradorAlerta?,
    onVolver: () -> Unit,
    onExito: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ProgramarAlertaViewModel = viewModel(key = "rf22-$idVehiculo") {
        ProgramarAlertaViewModel(repository, idVehiculo, borradorInicial)
    }
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.exito) { if (estado.exito != null) onExito() }
    ProgramarAlertaScreen(estado, vm::actualizar, vm::guardar, onVolver, modifier)
}

@Composable
fun ProgramarAlertaScreen(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoProgramarAlerta,
    onActualizar: ((BorradorAlerta) -> BorradorAlerta) -> Unit,
    onGuardar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val b = estado.borrador
    Scaffold(modifier = modifier, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            TextButton(onClick = onVolver) { Text("Volver") }
            Text("Programar alerta", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (estado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
            estado.contexto?.kmActual?.let { Text("Km base vigente: $it km") }
            estado.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            estado.contexto?.tipos?.let { tipos ->
                Text("Tipo de mantenimiento", style = MaterialTheme.typography.labelLarge)
                tipos.forEach { t ->
                    FilterChip(selected = b.codigoTipo == t.codigo, onClick = {
                        onActualizar { it.copy(codigoTipo = t.codigo) }
                    }, label = { Text(t.nombre) })
                }
            }
            listOf("POR_TIEMPO" to "Por tiempo", "POR_KILOMETRAJE" to "Por kilometraje", "AMBOS" to "Ambos").forEach { (c, l) ->
                FilterChip(selected = b.criterio == c, onClick = { onActualizar { it.copy(criterio = c) } }, label = { Text(l) })
            }
            if (b.criterio.contains("TIEMPO")) {
                OutlinedTextField(b.cantidadTiempo, { v -> onActualizar { it.copy(cantidadTiempo = v) } },
                    label = { Text("Cantidad") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = b.unidadTiempo == "SEMANAS",
                        onClick = { onActualizar { it.copy(unidadTiempo = "SEMANAS") } }, label = { Text("Semanas") })
                    FilterChip(selected = b.unidadTiempo == "MESES",
                        onClick = { onActualizar { it.copy(unidadTiempo = "MESES") } }, label = { Text("Meses") })
                }
            }
            if (b.criterio.contains("KILOMETRAJE")) {
                OutlinedTextField(b.intervaloKm, { v -> onActualizar { it.copy(intervaloKm = v) } },
                    label = { Text("Intervalo km") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            if (b.codigoTipo == "OTRO") {
                OutlinedTextField(b.servicioOtro, { v -> onActualizar { it.copy(servicioOtro = v) } },
                    label = { Text("Servicio OTRO") }, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = onGuardar, enabled = !estado.enviando && estado.contexto?.disponible == true,
                modifier = Modifier.fillMaxWidth()) {
                Text(if (estado.enviando) "Guardando…" else "Confirmar alerta")
            }
        }
    }
}
