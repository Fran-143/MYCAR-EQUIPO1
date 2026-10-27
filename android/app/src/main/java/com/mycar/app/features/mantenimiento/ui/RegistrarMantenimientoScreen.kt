package com.mycar.app.features.mantenimiento.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mycar.app.features.mantenimiento.domain.RepuestoBorrador
import com.mycar.app.features.mantenimiento.viewmodel.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun RegistrarMantenimientoScreen(
    s: MantenimientoUiState, vm: MantenimientoViewModel, modifier: Modifier,
    onCerrar: () -> Unit, onCerrarConfirmado: () -> Unit,
    onActualizarKm: ((Long) -> Unit)?, onSesionRequerida: (() -> Unit)?
) {
    val b = s.borrador
    val vehiculo = s.contexto?.vehiculos?.firstOrNull { it.idVehiculo == b.idVehiculo }
    val enabled = s.editable && !s.cargando
    val r = s.resultado
    if (r != null) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mantenimiento guardado", style = MaterialTheme.typography.headlineSmall)
            Text("Registro: ${r.idMantenimiento}")
            Text("Fecha del servicio: ${r.fechaServicio}")
            Text("Kilometraje del servicio: ${r.kmServicio} km")
            Text("Costo: ${r.costoTotal?.toPlainString() ?: "No informado"}")
            Text("Repuestos: ${r.cantidadRepuestos}")
            Text("Las recomendaciones se conservaron sin crear una alerta.")
            Button(onClick = onCerrarConfirmado) { Text("Volver") }
        }
        return
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Registrar mantenimiento", style = MaterialTheme.typography.headlineSmall)
        Text("Registra un servicio ya realizado para tu vehículo.")
        if (s.cargando || s.enviando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        s.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        s.errores.values.distinct().forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        if (s.incierto) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Resultado pendiente de verificar", style = MaterialTheme.typography.titleMedium)
                    Text("Conservamos la misma solicitud. Verifica antes de iniciar otro registro de este servicio.")
                    Button(onClick = vm::consultarOperacion, enabled = !s.enviando) { Text("Verificar resultado") }
                    if (s.contexto?.registroDisponible == true) {
                        OutlinedButton(onClick = vm::reintentarMismaOperacion, enabled = !s.enviando) {
                            Text("Reintentar la misma operación")
                        }
                    }
                    TextButton(onClick = onCerrar, enabled = !s.enviando) { Text("Salir conservando solicitud") }
                }
            }
        }
        OutlinedButton(onClick = vm::recargar, enabled = !s.enviando) { Text("Actualizar referencias") }
        if (onSesionRequerida != null) TextButton(onClick = onSesionRequerida, enabled = !s.enviando) {
            Text("Volver al inicio de sesión")
        }
        Selector("Vehículo", vehiculo?.let { "${it.placa} · ${it.marca} ${it.modelo}" } ?: "Seleccionar",
            s.contexto?.vehiculos.orEmpty().map { it.idVehiculo to "${it.placa} · ${it.marca} ${it.modelo}" },
            enabled, vm::solicitarVehiculo)
        if (s.contexto?.vehiculos?.isEmpty() == true) Text("No tienes vehículos activos disponibles.")
        Text("Kilometraje vigente (referencia): ${vehiculo?.kmActual?.let { "$it km" } ?: "Sin lectura"}")
        vehiculo?.fechaLecturaActual?.let { Text("Fecha de la lectura vigente: ${fechaLecturaLima(it)}") }
        Text("Aceite registrado: ${vehiculo?.aceiteRegistrado ?: "No informado"}")
        Text("Taller: Sin taller")
        OutlinedButton(
            onClick = { b.idVehiculo?.let { onActualizarKm?.invoke(it) } },
            enabled = enabled && b.idVehiculo != null && onActualizarKm != null
        ) { Text("Actualizar kilometraje vigente") }
        if (onActualizarKm == null) Text("La actualización del kilometraje aún no está disponible desde esta pantalla.")
        HorizontalDivider()
        Selector("Tipo de mantenimiento", s.contexto?.tipos?.firstOrNull { it.codigo == b.codigoTipo }?.nombre ?: "Seleccionar",
            s.contexto?.tipos.orEmpty().map { it.codigo to it.nombre }, enabled) { codigo ->
            vm.actualizar { it.copy(codigoTipo = codigo, servicioOtro = if (codigo == "OTRO") it.servicioOtro else "") }
        }
        if (b.codigoTipo == "OTRO") Campo("Servicio específico", b.servicioOtro, enabled,
            s.errores["servicioOtro"]) { v -> vm.actualizar { it.copy(servicioOtro = v) } }
        Fecha("Fecha del servicio", b.fecha, enabled, s.contexto?.fechaServidor, vm::fecha)
        Selector("Origen del kilometraje", if (b.origen == "LECTURA") "Lectura existente" else "Kilometraje histórico declarado",
            listOf("LECTURA" to "Lectura existente", "HISTORICO_MANUAL" to "Kilometraje histórico declarado"),
            enabled, vm::origen)
        if (b.origen == "LECTURA") {
            if (s.cargandoLecturas) Text("Cargando lecturas…")
            Selector("Lectura para el servicio", s.lecturas.firstOrNull { it.idLecturaKm == b.lectura }
                ?.let { "${it.valorKm} km · ${fechaLecturaLima(it.fechaLecturaUtc)}" } ?: "Seleccionar",
                s.lecturas.map { it.idLecturaKm to "${it.valorKm} km · ${fechaLecturaLima(it.fechaLecturaUtc)}" },
                enabled && !s.cargandoLecturas, vm::lectura)
            if (!s.cargandoLecturas && s.lecturas.isEmpty()) Text("No hay lecturas disponibles para esa fecha. Puedes declarar el kilometraje histórico.")
        }
        Campo("Kilometraje real del servicio", b.km, enabled && b.origen == "HISTORICO_MANUAL",
            s.errores["kmServicio"], KeyboardType.Number) { v -> vm.actualizar { it.copy(km = v) } }
        Text("Este dato corresponde al servicio realizado; no modifica el kilometraje vigente.")
        Campo("Descripción u observaciones (opcional)", b.observaciones, enabled,
            singleLine = false) { v -> vm.actualizar { it.copy(observaciones = v) } }
        Campo("Costo total (opcional)", b.costo, enabled, s.errores["costoTotal"], KeyboardType.Decimal) {
            v -> vm.actualizar { it.copy(costo = v) }
        }
        Text("Puedes dejar el costo vacío o indicar 0. Usa hasta dos decimales, sin separador de miles.")
        HorizontalDivider()
        Text("Repuestos opcionales", style = MaterialTheme.typography.titleMedium)
        b.repuestos.forEach { p ->
            key(p.idLocal) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.nombre, style = MaterialTheme.typography.titleSmall)
                        Text("Cantidad: ${p.cantidad} · Marca: ${p.marca.ifBlank { "No informada" }}")
                        Text("Precio unitario: ${p.precio.ifBlank { "No informado" }}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.editarRepuesto(p.idLocal) }, enabled = enabled) { Text("Editar") }
                            TextButton(onClick = { vm.quitarRepuesto(p.idLocal) }, enabled = enabled) { Text("Quitar") }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { vm.editarRepuesto() }, enabled = enabled) { Text("Agregar repuesto") }
        HorizontalDivider()
        Text("Próximo mantenimiento (opcional)", style = MaterialTheme.typography.titleMedium)
        Fecha("Próxima fecha sugerida", b.proximaFecha, enabled, null, { v ->
            vm.actualizar { it.copy(proximaFecha = v) }
        }, opcional = true)
        Campo("Próximo kilometraje sugerido", b.proximoKm, enabled,
            s.errores["proximoKmSugerido"], KeyboardType.Number) { v -> vm.actualizar { it.copy(proximoKm = v) } }
        if (b.proximaFecha.isNotBlank() || b.proximoKm.isNotBlank()) {
            OutlinedButton(onClick = {}, enabled = false) { Text("Sí, crear alerta · Próximamente") }
        }
        Button(onClick = vm::guardar, enabled = enabled && s.contexto?.registroDisponible == true,
            modifier = Modifier.fillMaxWidth()) {
            Text(if (s.enviando) "Guardando…" else if (b.proximaFecha.isNotBlank() || b.proximoKm.isNotBlank())
                "No, solo guardar" else "Guardar mantenimiento")
        }
        TextButton(onClick = vm::solicitarSalir, enabled = enabled) { Text("Cancelar") }
        Spacer(Modifier.height(20.dp))
    }
    if (s.confirmarSalida) AlertDialog(
        onDismissRequest = vm::seguirEditando,
        title = { Text("¿Descartar el borrador?") },
        text = { Text("Se descartará este mantenimiento sin guardar. El kilometraje que ya hayas confirmado permanece.") },
        confirmButton = { TextButton(onClick = { vm.descartar(); onCerrar() }) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = vm::seguirEditando) { Text("Seguir editando") } }
    )
    if (s.vehiculoPendiente != null) AlertDialog(
        onDismissRequest = vm::cancelarVehiculo,
        title = { Text("¿Cambiar de vehículo?") }, text = { Text("El nuevo vehículo comenzará con un formulario vacío.") },
        confirmButton = { TextButton(onClick = vm::confirmarVehiculo) { Text("Cambiar") } },
        dismissButton = { TextButton(onClick = vm::cancelarVehiculo) { Text("Conservar borrador") } }
    )
    s.editor?.let { p -> EditorRepuesto(p, s.erroresEditor, vm) }
}

@Composable
private fun EditorRepuesto(p: RepuestoBorrador, errores: Map<String,String>, vm: MantenimientoViewModel) {
    AlertDialog(onDismissRequest = vm::cerrarEditor, title = { Text("Repuesto del mantenimiento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo("Nombre", p.nombre, true, errores["nombre"]) { v -> vm.cambiarEditor { it.copy(nombre = v) } }
                Campo("Marca (opcional)", p.marca, true, errores["marca"]) { v -> vm.cambiarEditor { it.copy(marca = v) } }
                Campo("Cantidad", p.cantidad, true, errores["cantidad"], KeyboardType.Number) { v -> vm.cambiarEditor { it.copy(cantidad = v) } }
                Campo("Precio unitario (opcional)", p.precio, true, errores["precio"], KeyboardType.Decimal) {
                    v -> vm.cambiarEditor { it.copy(precio = v) }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::confirmarRepuesto) { Text("Conservar repuesto") } },
        dismissButton = { TextButton(onClick = vm::cerrarEditor) { Text("Cancelar") } }
    )
}

@Composable
private fun Campo(label: String, value: String, enabled: Boolean, error: String? = null,
                  teclado: KeyboardType = KeyboardType.Text, singleLine: Boolean = true,
                  onValue: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) },
        enabled = enabled, isError = error != null, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = teclado), modifier = Modifier.fillMaxWidth(),
        supportingText = { if (error != null) Text(error) })
}

@Composable
private fun <T> Selector(label: String, value: String, opciones: List<Pair<T,String>>,
                         enabled: Boolean, onSelect: (T) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { abierto = true }, enabled = enabled && opciones.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()) { Text(value) }
            DropdownMenu(expanded = abierto && enabled, onDismissRequest = { abierto = false },
                modifier = Modifier.heightIn(max = 260.dp)) {
                opciones.forEach { (clave, texto) -> DropdownMenuItem(text = { Text(texto) },
                    onClick = { abierto = false; onSelect(clave) }) }
            }
        }
    }
}

@Composable
private fun Fecha(label: String, value: String, enabled: Boolean, maximo: String?,
                  onValue: (String) -> Unit, opcional: Boolean = false) {
    val context = LocalContext.current
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val inicial = runCatching { LocalDate.parse(value) }.getOrElse {
                    runCatching { LocalDate.parse(maximo) }.getOrDefault(LocalDate.now())
                }
                val dialog = DatePickerDialog(context, { _, y, m, d ->
                    onValue(LocalDate.of(y, m + 1, d).toString())
                }, inicial.year, inicial.monthValue - 1, inicial.dayOfMonth)
                maximo?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let {
                    dialog.datePicker.maxDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                dialog.show()
            }, enabled = enabled) { Text(value.ifBlank { "Elegir fecha" }) }
            if (opcional && value.isNotBlank()) TextButton(onClick = { onValue("") }, enabled = enabled) { Text("Quitar") }
        }
    }
}

private fun fechaLecturaLima(fecha: String): String = runCatching {
    OffsetDateTime.parse(fecha).atZoneSameInstant(ZoneId.of("America/Lima"))
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " (Lima)"
}.getOrDefault("Fecha no disponible")
