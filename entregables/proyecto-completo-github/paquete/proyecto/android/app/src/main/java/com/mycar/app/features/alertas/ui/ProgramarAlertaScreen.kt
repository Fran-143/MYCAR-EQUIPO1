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
import com.mycar.app.features.alertas.domain.CriterioAlerta
import com.mycar.app.features.alertas.viewmodel.BorradorAlerta
import com.mycar.app.features.alertas.viewmodel.PasoProgramar
import com.mycar.app.features.alertas.viewmodel.ProgramarAlertaViewModel

@Composable
fun ProgramarAlertaRoute(
    repository: AlertasRepository,
    idVehiculo: Long,
    sesionFormulario: Int,
    borradorInicial: BorradorAlerta?,
    onVolver: () -> Unit,
    onExito: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ProgramarAlertaViewModel = viewModel(key = "rf22-$idVehiculo-s$sesionFormulario") {
        ProgramarAlertaViewModel(repository, idVehiculo, borradorInicial)
    }
    val estado by vm.estado.collectAsStateWithLifecycle()
    ProgramarAlertaScreen(
        estado,
        vm::actualizar, vm::revisar, vm::confirmar, vm::volverFormulario, vm::cargar,
        onVolver, onExito, modifier
    )
}

@Composable
fun ProgramarAlertaScreen(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoProgramarAlerta,
    onActualizar: ((BorradorAlerta) -> BorradorAlerta) -> Unit,
    onRevisar: () -> Unit,
    onConfirmar: () -> Unit,
    onVolverFormulario: () -> Unit,
    onRecargar: () -> Unit,
    onVolver: () -> Unit,
    onExitoVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (estado.paso) {
        PasoProgramar.EXITO -> PantallaExitoAlerta(estado.exito, onExitoVolver, modifier)
        PasoProgramar.REVISION -> PantallaRevisionAlerta(
            estado, onConfirmar, onVolverFormulario, modifier
        )
        PasoProgramar.FORMULARIO -> PantallaFormularioAlerta(
            estado, onActualizar, onRevisar, onRecargar, onVolver, modifier
        )
    }
}

@Composable
private fun PantallaExitoAlerta(idAlerta: Long?, onVerAlertas: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Alerta programada", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (idAlerta != null) "La alerta #$idAlerta quedó registrada."
            else "La alerta quedó registrada."
        )
        Button(onClick = onVerAlertas, modifier = Modifier.fillMaxWidth()) {
            Text("Ver alertas")
        }
    }
}

@Composable
private fun PantallaRevisionAlerta(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoProgramarAlerta,
    onConfirmar: () -> Unit,
    onVolverFormulario: () -> Unit,
    modifier: Modifier
) {
    val b = estado.borrador
    val r = estado.resumen
    val ctx = estado.contexto
    Scaffold(modifier = modifier, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            TextButton(onClick = onVolverFormulario, enabled = !estado.enviando) { Text("Volver") }
            Text("Revisar alerta", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (estado.basesCambiaron) {
                Text(
                    estado.mensaje ?: "Las bases cambiaron. Confirma de nuevo con los valores actualizados.",
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                estado.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Text("Tipo: ${ctx?.tipos?.firstOrNull { it.codigo == b.codigoTipo }?.nombre ?: b.codigoTipo}")
            if (b.codigoTipo == "OTRO") Text("Servicio: ${b.servicioOtro}")
            Text("Criterio: ${etiquetaCriterio(b.criterio)}")
            if (CriterioAlerta.usaTiempo(b.criterio)) {
                Text("Intervalo temporal: ${b.cantidadTiempo} ${b.unidadTiempo.lowercase()}")
            }
            if (CriterioAlerta.usaKm(b.criterio)) {
                Text("Intervalo km: ${b.intervaloKm} km")
            }
            Text(
                "Fecha base (hoy Lima): ${r?.fechaBase ?: "—"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Último kilometraje registrado (base): ${r?.kmBase?.let { "$it km" } ?: "No registrado"}",
                style = MaterialTheme.typography.bodyMedium
            )
            r?.fechaObjetivo?.let {
                Text("Fecha objetivo (umbral de programación): $it")
            }
            r?.kmObjetivo?.let {
                Text("Kilometraje objetivo (umbral registrado): $it km")
            }
            if (b.criterio == "AMBOS") {
                Text(
                    "Ambos: se inicia la atención cuando se alcance el primero de los dos umbrales.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val ref = r?.referencia
            if (ref != null) {
                Text(
                    "Referencia del último mantenimiento (no entra en el cálculo): ${ref.fecha}" +
                        (ref.servicioOtro?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    "Sin mantenimiento previo de referencia.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "Estos valores son los que se guardarán al confirmar. Aún no está programada.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onConfirmar,
                enabled = !estado.enviando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (estado.enviando) "Guardando…" else "Programar alerta")
            }
            OutlinedButton(
                onClick = onVolverFormulario,
                enabled = !estado.enviando,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Editar configuración") }
        }
    }
}

@Composable
private fun PantallaFormularioAlerta(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoProgramarAlerta,
    onActualizar: ((BorradorAlerta) -> BorradorAlerta) -> Unit,
    onRevisar: () -> Unit,
    onRecargar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier
) {
    val b = estado.borrador
    val tipos = estado.contexto?.tipos.orEmpty()
    val editable = !estado.enviando && estado.exito == null
    Scaffold(modifier = modifier, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            TextButton(onClick = onVolver, enabled = !estado.enviando) { Text("Volver") }
            Text("Programar alerta", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (estado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Datos actuales", style = MaterialTheme.typography.titleMedium)
            Text(
                "Fecha de hoy (Lima), usada para calcular el objetivo: " +
                    (estado.contexto?.fechaServidor?.take(10) ?: "—")
            )
            Text(
                "Último kilometraje registrado (no el odómetro en vivo): " +
                    (estado.contexto?.kmActual?.let { "$it km" } ?: "No registrado")
            )
            estado.mensaje?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                if (estado.contexto == null && !estado.cargando) {
                    OutlinedButton(onClick = onRecargar, enabled = editable) { Text("Reintentar carga") }
                }
            }

            Text("Tipo de mantenimiento *", style = MaterialTheme.typography.labelLarge)
            if (tipos.isEmpty() && !estado.cargando) {
                Text("No hay tipos disponibles. Comprueba la conexión e inténtalo de nuevo.")
            } else {
                SelectorTipoAlerta(
                    etiqueta = tipos.firstOrNull { it.codigo == b.codigoTipo }?.nombre ?: "Seleccionar tipo",
                    opciones = tipos.map { it.codigo to it.nombre },
                    seleccionado = b.codigoTipo,
                    enabled = editable
                ) { codigo ->
                    onActualizar {
                        it.copy(
                            codigoTipo = codigo,
                            servicioOtro = if (codigo == "OTRO") it.servicioOtro else ""
                        )
                    }
                }
            }
            estado.errores["codigoTipo"]?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Text("Criterio *", style = MaterialTheme.typography.labelLarge)
            listOf(
                "POR_TIEMPO" to "Por tiempo",
                "POR_KILOMETRAJE" to "Por kilometraje",
                "AMBOS" to "Ambos"
            ).forEach { (c, l) ->
                FilterChip(
                    selected = b.criterio == c,
                    onClick = { if (editable) onActualizar { it.copy(criterio = c) } },
                    enabled = editable,
                    label = { Text(l) }
                )
            }
            if (CriterioAlerta.usaTiempo(b.criterio)) {
                OutlinedTextField(
                    b.cantidadTiempo,
                    { v -> onActualizar { it.copy(cantidadTiempo = v) } },
                    label = { Text("Cantidad") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editable,
                    isError = estado.errores.containsKey("cantidadTiempo"),
                    supportingText = estado.errores["cantidadTiempo"]?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = b.unidadTiempo == "SEMANAS",
                        onClick = { if (editable) onActualizar { it.copy(unidadTiempo = "SEMANAS") } },
                        enabled = editable,
                        label = { Text("Semanas") }
                    )
                    FilterChip(
                        selected = b.unidadTiempo == "MESES",
                        onClick = { if (editable) onActualizar { it.copy(unidadTiempo = "MESES") } },
                        enabled = editable,
                        label = { Text("Meses") }
                    )
                }
            }
            if (CriterioAlerta.usaKm(b.criterio)) {
                OutlinedTextField(
                    b.intervaloKm,
                    { v -> onActualizar { it.copy(intervaloKm = v) } },
                    label = { Text("Intervalo km") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editable,
                    isError = estado.errores.containsKey("intervaloKm"),
                    supportingText = estado.errores["intervaloKm"]?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            if (b.codigoTipo == "OTRO") {
                OutlinedTextField(
                    b.servicioOtro,
                    { v -> onActualizar { it.copy(servicioOtro = v) } },
                    label = { Text("Especificar servicio") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editable,
                    isError = estado.errores.containsKey("servicioOtro"),
                    supportingText = estado.errores["servicioOtro"]?.let { { Text(it) } }
                )
            }
            Button(
                onClick = onRevisar,
                enabled = editable && !estado.cargando && estado.contexto?.disponible == true && tipos.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Revisar y programar")
            }
        }
    }
}

@Composable
private fun SelectorTipoAlerta(
    etiqueta: String,
    opciones: List<Pair<String, String>>,
    seleccionado: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { abierto = true },
            enabled = enabled && opciones.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(etiqueta, modifier = Modifier.weight(1f))
            Text("⌄")
        }
        DropdownMenu(
            expanded = abierto && enabled,
            onDismissRequest = { abierto = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            opciones.forEach { (codigo, nombre) ->
                DropdownMenuItem(
                    text = { Text(if (codigo == seleccionado) "✓ $nombre" else nombre) },
                    onClick = { abierto = false; onSelect(codigo) }
                )
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
