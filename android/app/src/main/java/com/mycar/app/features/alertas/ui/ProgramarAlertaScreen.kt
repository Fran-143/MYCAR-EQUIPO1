package com.mycar.app.features.alertas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.CriterioAlerta
import com.mycar.app.features.alertas.domain.ReferenciaMantenimientoAlerta
import com.mycar.app.features.alertas.domain.TipoAlerta
import com.mycar.app.features.alertas.viewmodel.BorradorAlerta
import com.mycar.app.features.alertas.viewmodel.PasoProgramar
import com.mycar.app.features.alertas.viewmodel.ProgramarAlertaViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgramarAlertaRoute(
    repository: AlertasRepository,
    idVehiculo: Long,
    vehiculos: List<Pair<Long, String>>,
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
        estado = estado,
        vehiculos = vehiculos,
        onActualizar = vm::actualizar,
        onCambiarVehiculo = vm::cambiarVehiculo,
        onRevisar = vm::revisar,
        onConfirmar = vm::confirmar,
        onVolverFormulario = vm::volverFormulario,
        onRecargar = vm::cargar,
        onVolver = onVolver,
        onExitoVolver = onExito,
        modifier = modifier
    )
}

@Composable
fun ProgramarAlertaScreen(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoProgramarAlerta,
    vehiculos: List<Pair<Long, String>> = emptyList(),
    onActualizar: ((BorradorAlerta) -> BorradorAlerta) -> Unit,
    onCambiarVehiculo: (Long) -> Unit = {},
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
        PasoProgramar.REVISION -> PantallaRevisionAlerta(estado, onConfirmar, onVolverFormulario, modifier)
        PasoProgramar.FORMULARIO -> PantallaFormularioAlerta(
            estado, vehiculos, onActualizar, onCambiarVehiculo, onRevisar, onRecargar, onVolver, modifier
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
            Text("Revisar programación", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
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
            Text(
                "Origen: " + if (b.origenCalculo == "DESDE_MANTENIMIENTO") "Desde un mantenimiento realizado"
                else "Desde hoy"
            )
            if (CriterioAlerta.usaTiempo(b.criterio)) {
                Text("Intervalo temporal: ${b.cantidadTiempo} ${b.unidadTiempo.lowercase()}")
            }
            if (CriterioAlerta.usaKm(b.criterio)) {
                Text("Intervalo km: ${b.intervaloKm} km")
            }
            Text("Fecha base: ${r?.fechaBase ?: "—"}")
            Text("Kilometraje base: ${r?.kmBase?.let { "$it km" } ?: "—"}")
            r?.fechaObjetivo?.let { Text("Fecha objetivo: $it") }
            r?.kmObjetivo?.let { Text("Kilometraje objetivo: $it km") }
            r?.referencia?.let { ref ->
                Text(
                    "Mantenimiento de referencia: ${etiquetaMantenimiento(nombreServicio(ref, ctx?.tipos.orEmpty()), ref)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                "Estos valores se guardarán al confirmar. Aún no está programada.",
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
    vehiculos: List<Pair<Long, String>>,
    onActualizar: ((BorradorAlerta) -> BorradorAlerta) -> Unit,
    onCambiarVehiculo: (Long) -> Unit,
    onRevisar: () -> Unit,
    onRecargar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier
) {
    val ctx = estado.contexto
    if (ctx == null) {
        PantallaFormularioAlertaSinContexto(estado, onVolver, onRecargar, modifier)
        return
    }
    val b = estado.borrador
    val tipos = ctx.tipos
    val refs = ProgramarAlertaViewModel.referenciasElegibles(b, ctx)
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
            estado.mensaje?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                if (estado.contexto == null && !estado.cargando) {
                    OutlinedButton(onClick = onRecargar, enabled = editable) { Text("Reintentar carga") }
                }
            }

            Text("Seleccionar vehículo", style = MaterialTheme.typography.labelLarge)
            if (vehiculos.isEmpty()) {
                Text("No hay vehículos activos para programar alertas.")
            } else {
                SelectorSimple(
                    etiqueta = vehiculos.firstOrNull { it.first == estado.idVehiculo }?.second
                        ?: "Seleccionar vehículo",
                    opciones = vehiculos.map { it.first.toString() to it.second },
                    seleccionado = estado.idVehiculo.toString(),
                    enabled = editable && vehiculos.size > 1
                ) { id -> onCambiarVehiculo(id.toLong()) }
            }
            Text(
                "Se mostrarán únicamente tus vehículos activos.",
                style = MaterialTheme.typography.bodySmall
            )

            Text("Tipo de servicio", style = MaterialTheme.typography.labelLarge)
            if (tipos.isEmpty() && !estado.cargando) {
                Text("No hay tipos disponibles. Comprueba la conexión e inténtalo de nuevo.")
            } else {
                SelectorSimple(
                    etiqueta = tipos.firstOrNull { it.codigo == b.codigoTipo }?.nombre ?: "Seleccionar tipo",
                    opciones = tipos.map { it.codigo to it.nombre },
                    seleccionado = b.codigoTipo,
                    enabled = editable
                ) { codigo ->
                    onActualizar {
                        it.copy(
                            codigoTipo = codigo,
                            servicioOtro = if (codigo == "OTRO") it.servicioOtro else "",
                            idReferencia = null
                        )
                    }
                }
            }
            estado.errores["codigoTipo"]?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (b.codigoTipo == "OTRO") {
                OutlinedTextField(
                    b.servicioOtro,
                    { v -> onActualizar { it.copy(servicioOtro = v, idReferencia = null) } },
                    label = { Text("Especificar servicio") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editable,
                    isError = estado.errores.containsKey("servicioOtro"),
                    supportingText = estado.errores["servicioOtro"]?.let { { Text(it) } }
                )
            }

            Text("Tipo de alerta", style = MaterialTheme.typography.labelLarge)
            listOf(
                "POR_TIEMPO" to "Por tiempo",
                "POR_KILOMETRAJE" to "Por kilometraje",
                "AMBOS" to "Por tiempo y kilometraje"
            ).forEach { (c, l) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = b.criterio == c,
                            enabled = editable,
                            role = Role.RadioButton,
                            onClick = { onActualizar { it.copy(criterio = c) } }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = b.criterio == c, onClick = null, enabled = editable)
                    Text(l, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Text("¿Desde cuándo calcular?", style = MaterialTheme.typography.labelLarge)
            SelectorSimple(
                etiqueta = if (b.origenCalculo == "DESDE_MANTENIMIENTO") "Desde un mantenimiento realizado"
                else "Desde hoy",
                opciones = listOf(
                    "DESDE_HOY" to "Desde hoy",
                    "DESDE_MANTENIMIENTO" to "Desde un mantenimiento realizado"
                ),
                seleccionado = b.origenCalculo,
                enabled = editable
            ) { origen ->
                onActualizar { it.copy(origenCalculo = origen, idReferencia = null) }
            }

            if (b.origenCalculo == "DESDE_HOY") {
                Text("Datos actuales del vehículo", style = MaterialTheme.typography.titleMedium)
                Text("Fecha: ${estado.contexto?.fechaServidor?.take(10) ?: "—"}")
                Text("Kilometraje: ${estado.contexto?.kmActual?.let { "%,d km".format(it).replace(',', ' ') } ?: "No registrado"}")
            } else {
                Text("Selecciona el mantenimiento registrado", style = MaterialTheme.typography.labelLarge)
                if (refs.isEmpty()) {
                    Text(
                        "No hay mantenimientos registrados para este vehículo y servicio.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    val seleccion = refs.firstOrNull { it.id == b.idReferencia }
                    SelectorSimple(
                        etiqueta = seleccion?.let {
                            etiquetaMantenimiento(nombreServicio(it, tipos), it)
                        } ?: "Seleccionar mantenimiento",
                        opciones = refs.map {
                            it.id.toString() to etiquetaMantenimiento(nombreServicio(it, tipos), it)
                        },
                        seleccionado = b.idReferencia?.toString().orEmpty(),
                        enabled = editable
                    ) { id -> onActualizar { it.copy(idReferencia = id.toLongOrNull()) } }
                    Text(
                        "Se muestra el nombre del servicio, la fecha y el kilometraje del mantenimiento.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                estado.errores["idReferencia"]?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (CriterioAlerta.usaTiempo(b.criterio)) {
                Text("Intervalo de tiempo", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        b.cantidadTiempo,
                        { v -> onActualizar { it.copy(cantidadTiempo = v) } },
                        modifier = Modifier.weight(1f),
                        enabled = editable,
                        isError = estado.errores.containsKey("cantidadTiempo"),
                        supportingText = estado.errores["cantidadTiempo"]?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    SelectorSimple(
                        etiqueta = if (b.unidadTiempo == "SEMANAS") "Semanas" else "Meses",
                        opciones = listOf("SEMANAS" to "Semanas", "MESES" to "Meses"),
                        seleccionado = b.unidadTiempo,
                        enabled = editable,
                        modifier = Modifier.weight(1f)
                    ) { u -> onActualizar { it.copy(unidadTiempo = u) } }
                }
            }
            if (CriterioAlerta.usaKm(b.criterio)) {
                OutlinedTextField(
                    b.intervaloKm,
                    { v -> onActualizar { it.copy(intervaloKm = v) } },
                    label = { Text("Intervalo de kilometraje (km)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editable,
                    isError = estado.errores.containsKey("intervaloKm"),
                    supportingText = estado.errores["intervaloKm"]?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            TarjetaProximoMantenimiento(estado.preview)

            Button(
                onClick = onRevisar,
                enabled = editable && !estado.cargando && estado.contexto?.disponible == true && tipos.isNotEmpty() && vehiculos.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Revisar programación")
            }
        }
    }
}

@Composable
private fun PantallaFormularioAlertaSinContexto(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoProgramarAlerta,
    onVolver: () -> Unit,
    onRecargar: () -> Unit,
    modifier: Modifier
) {
    Scaffold(modifier = modifier, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            TextButton(onClick = onVolver) { Text("Volver") }
            Text("Programar alerta", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (estado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
            estado.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = onRecargar) { Text("Reintentar carga") }
        }
    }
}

@Composable
private fun TarjetaProximoMantenimiento(
    preview: com.mycar.app.features.alertas.viewmodel.ResumenAlerta?
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Próximo mantenimiento", style = MaterialTheme.typography.titleMedium)
            if (preview == null) {
                Text("Completa los datos para ver la vista previa.")
            } else {
                preview.fechaObjetivo?.let { Text("Fecha objetivo: $it") }
                preview.kmObjetivo?.let { Text("KM objetivo: $it km") }
                if (preview.fechaObjetivo == null && preview.kmObjetivo == null) {
                    Text("Faltan datos para calcular el próximo mantenimiento.")
                }
            }
        }
    }
}

@Composable
private fun SelectorSimple(
    etiqueta: String,
    opciones: List<Pair<String, String>>,
    seleccionado: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
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

private fun nombreServicio(ref: ReferenciaMantenimientoAlerta, tipos: List<TipoAlerta>): String {
    if (ref.codigoTipo == "OTRO") {
        return ref.servicioOtro?.takeIf { it.isNotBlank() } ?: "Otro servicio"
    }
    return tipos.firstOrNull { it.codigo == ref.codigoTipo }?.nombre
        ?: ref.codigoTipo.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

private fun etiquetaMantenimiento(nombre: String, ref: ReferenciaMantenimientoAlerta): String {
    val fecha = formatearFechaCorta(ref.fecha)
    val km = ref.kmServicio?.let { "%,d km".format(it).replace(',', ' ') } ?: "— km"
    return "$nombre · $fecha · $km"
}

private fun formatearFechaCorta(iso: String): String = runCatching {
    LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault(iso.take(10))
