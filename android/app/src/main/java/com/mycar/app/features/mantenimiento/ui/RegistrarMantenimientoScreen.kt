package com.mycar.app.features.mantenimiento.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mycar.app.features.mantenimiento.viewmodel.MantenimientoUiState
import com.mycar.app.features.mantenimiento.viewmodel.MantenimientoViewModel

@Composable
internal fun RegistrarMantenimientoScreen(
    s: MantenimientoUiState, vm: MantenimientoViewModel, modifier: Modifier,
    onCerrar: () -> Unit, onCerrarConfirmado: () -> Unit,
    onActualizarKm: ((Long) -> Unit)?, onSesionRequerida: (() -> Unit)?
) {
    val acciones = remember(vm) { AccionesDesdeViewModel(vm) }
    FormularioRegistroMantenimiento(s, acciones, modifier, onCerrar, onCerrarConfirmado,
        onActualizarKm, onSesionRequerida)
}

@Composable
internal fun FormularioRegistroMantenimiento(
    s: MantenimientoUiState, vm: AccionesMantenimiento, modifier: Modifier,
    onCerrar: () -> Unit, onCerrarConfirmado: () -> Unit,
    onActualizarKm: ((Long) -> Unit)?, onSesionRequerida: (() -> Unit)?
) {
    val b = s.borrador
    val vehiculo = s.contexto?.vehiculos?.firstOrNull { it.idVehiculo == b.idVehiculo }
    val enabled = s.editable && !s.cargando
    s.resultado?.let {
        ResultadoMantenimientoScreen(it, modifier, onCerrarConfirmado)
        return
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                when {
                    s.requiereSesion || s.incierto -> onCerrar()
                    else -> vm.solicitarSalir()
                }
            }, enabled = !s.enviando, modifier = Modifier.testTag("rf17.volver")) { Text("Volver") }
            Text("Mantenimientos", style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Registrar mantenimiento", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Cuida el historial de tu vehículo. Registra un servicio que ya realizaste.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.requiereSesion) {
                AvisoMantenimiento("Inicia sesión para elegir tu vehículo y registrar un mantenimiento.")
                onSesionRequerida?.let { TextButton(onClick = it) { Text("Iniciar sesión") } }
            }
            if (s.cargando || s.enviando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("rf17.cargando"))
                Text(if (s.enviando) "Guardando el mantenimiento…" else "Cargando tus vehículos y tipos de mantenimiento…",
                    style = MaterialTheme.typography.bodySmall)
            }
            s.mensaje?.let { AvisoMantenimiento(it, error = true) }
            if (s.errores.isNotEmpty()) AvisoMantenimiento("Revisa los campos marcados antes de guardar.", error = true)
            if (s.incierto) {
                OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Resultado pendiente de verificar", style = MaterialTheme.typography.titleMedium)
                        Text("Conservamos tu solicitud. Verifica el resultado antes de registrar otra vez este servicio.")
                        Button(onClick = vm::consultarOperacion, enabled = !s.enviando,
                            modifier = Modifier.testTag("rf17.verificar")) { Text("Verificar resultado") }
                        if (s.contexto?.registroDisponible == true) {
                            OutlinedButton(onClick = vm::reintentarMismaOperacion, enabled = !s.enviando) {
                                Text("Reintentar la misma operación")
                            }
                        }
                        TextButton(onClick = onCerrar, enabled = !s.enviando) { Text("Salir conservando solicitud") }
                    }
                }
            }
            if (!s.requiereSesion) TextButton(onClick = vm::recargar, enabled = !s.enviando) { Text("Actualizar referencias") }
            SeccionMantenimiento("1", "Tu vehículo") {
                SelectorMantenimiento("Vehículo *", vehiculo?.let { "${it.placa} · ${it.marca} ${it.modelo}" } ?: "Seleccionar vehículo",
                    s.contexto?.vehiculos.orEmpty().map { it.idVehiculo to "${it.placa} · ${it.marca} ${it.modelo}" },
                    enabled, s.errores["idVehiculo"], onSelect = vm::solicitarVehiculo)
                if (s.contexto?.vehiculos?.isEmpty() == true) Text("No tienes vehículos activos disponibles.")
                HorizontalDivider()
                Text("Kilometraje vigente: ${vehiculo?.kmActual?.let { "$it km" } ?: "Sin lectura"}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                vehiculo?.fechaLecturaActual?.let { Text(fechaLecturaVisible(it), style = MaterialTheme.typography.bodySmall) }
                Text("Aceite registrado: ${vehiculo?.aceiteRegistrado ?: "No informado"}", style = MaterialTheme.typography.bodySmall)
                Text("Taller: Sin taller", style = MaterialTheme.typography.bodySmall)
                if (!s.requiereSesion) {
                    OutlinedButton(onClick = { b.idVehiculo?.let { onActualizarKm?.invoke(it) } },
                        enabled = enabled && b.idVehiculo != null && onActualizarKm != null) { Text("Actualizar kilometraje vigente") }
                    if (onActualizarKm == null) Text("La actualización del kilometraje aún no está disponible desde esta pantalla.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            SeccionMantenimiento("2", "Detalle del servicio", "Los campos con * son obligatorios.") {
                SelectorMantenimiento("Tipo de mantenimiento *",
                    s.contexto?.tipos?.firstOrNull { it.codigo == b.codigoTipo }?.nombre ?: "Seleccionar tipo",
                    s.contexto?.tipos.orEmpty().map { it.codigo to it.nombre }, enabled,
                    s.errores["codigoTipoMantenimiento"], Modifier.testTag("rf17.tipo")) { codigo ->
                    vm.actualizar { it.copy(codigoTipo = codigo, servicioOtro = if (codigo == "OTRO") it.servicioOtro else "") }
                }
                if (b.codigoTipo == "OTRO") CampoMantenimiento("Servicio específico *", b.servicioOtro, enabled,
                    s.errores["servicioOtro"], modifier = Modifier.testTag("rf17.servicioOtro")) {
                    v -> vm.actualizar { it.copy(servicioOtro = v) }
                }
                FechaMantenimiento("Fecha del servicio *", b.fecha, enabled, s.contexto?.fechaServidor,
                    vm::fecha, error = s.errores["fechaServicio"])
                SelectorMantenimiento("Origen del kilometraje *",
                    if (b.origen == "LECTURA") "Lectura existente" else "Kilometraje histórico declarado",
                    listOf("LECTURA" to "Lectura existente", "HISTORICO_MANUAL" to "Kilometraje histórico declarado"),
                    enabled, s.errores["origenKmServicio"], onSelect = vm::origen)
                if (b.origen == "LECTURA") {
                    if (s.cargandoLecturas) Text("Cargando lecturas…", style = MaterialTheme.typography.bodySmall)
                    SelectorMantenimiento("Lectura para el servicio *",
                        s.lecturas.firstOrNull { it.idLecturaKm == b.lectura }?.let { "${it.valorKm} km · ${fechaLecturaVisible(it.fechaLecturaUtc)}" }
                            ?: "Seleccionar lectura",
                        s.lecturas.map { it.idLecturaKm to "${it.valorKm} km · ${fechaLecturaVisible(it.fechaLecturaUtc)}" },
                        enabled && !s.cargandoLecturas, s.errores["idLecturaKmReferencia"], onSelect = vm::lectura)
                    if (!s.requiereSesion && !s.cargandoLecturas && s.lecturas.isEmpty())
                        Text("No hay lecturas para esa fecha. Puedes declarar el kilometraje histórico.", style = MaterialTheme.typography.bodySmall)
                }
                CampoMantenimiento("Kilometraje real del servicio *", b.km, enabled && b.origen == "HISTORICO_MANUAL",
                    s.errores["kmServicio"], KeyboardType.Number, modifier = Modifier.testTag("rf17.km")) {
                    v -> vm.actualizar { it.copy(km = v) }
                }
                Text("Corresponde al servicio realizado. No modifica el kilometraje vigente.", style = MaterialTheme.typography.bodySmall)
            }
            SeccionMantenimiento("3", "Costo y observaciones", "Estos datos son opcionales.") {
                CampoMantenimiento("Costo total", b.costo, enabled, s.errores["costoTotal"], KeyboardType.Decimal) {
                    v -> vm.actualizar { it.copy(costo = v) }
                }
                Text("Puedes dejarlo vacío o indicar 0. Usa hasta dos decimales, sin separador de miles.", style = MaterialTheme.typography.bodySmall)
                CampoMantenimiento("Descripción u observaciones", b.observaciones, enabled,
                    s.errores["observaciones"], singleLine = false) { v -> vm.actualizar { it.copy(observaciones = v) } }
            }
            SeccionMantenimiento("4", "Repuestos", "Añade los repuestos utilizados en este servicio, si los hubo.") {
                if (b.repuestos.isEmpty()) Text("Aún no agregaste repuestos.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                b.repuestos.forEach { p ->
                    key(p.idLocal) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(p.nombre, style = MaterialTheme.typography.titleSmall)
                                Text("${p.cantidad} unidad(es) · ${p.marca.ifBlank { "Marca no informada" }}", style = MaterialTheme.typography.bodySmall)
                                Text("Precio unitario: ${p.precio.ifBlank { "No informado" }}", style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(onClick = { vm.editarRepuesto(p.idLocal) }, enabled = enabled) { Text("Editar") }
                                    TextButton(onClick = { vm.quitarRepuesto(p.idLocal) }, enabled = enabled) { Text("Quitar") }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { vm.editarRepuesto() }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("rf17.agregarRepuesto")) { Text("Agregar repuesto") }
                s.errores.filterKeys { it.startsWith("repuestos[") }.values.distinct().forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            SeccionMantenimiento("5", "Próxima recomendación", "Opcional. Se conserva como referencia para el siguiente servicio.") {
                FechaMantenimiento("Próxima fecha sugerida", b.proximaFecha, enabled, null,
                    { v -> vm.actualizar { it.copy(proximaFecha = v) } }, opcional = true,
                    error = s.errores["proximaFechaSugerida"])
                CampoMantenimiento("Próximo kilometraje sugerido", b.proximoKm, enabled,
                    s.errores["proximoKmSugerido"], KeyboardType.Number) { v -> vm.actualizar { it.copy(proximoKm = v) } }
                if (b.proximaFecha.isNotBlank() || b.proximoKm.isNotBlank()) {
                    OutlinedButton(onClick = {}, enabled = false) { Text("Sí, crear alerta · Próximamente") }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = vm::guardar, enabled = enabled && s.contexto?.registroDisponible == true,
                modifier = Modifier.fillMaxWidth().testTag("rf17.guardar"),
                shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(14.dp)) {
                Text(if (s.enviando) "Guardando…" else if (b.proximaFecha.isNotBlank() || b.proximoKm.isNotBlank())
                    "No, solo guardar" else "Guardar mantenimiento")
            }
            TextButton(onClick = { if (s.requiereSesion) onCerrar() else vm.solicitarSalir() },
                enabled = !s.enviando && !s.incierto, modifier = Modifier.testTag("rf17.cancelar")) { Text("Cancelar") }
        }
    }
    if (s.confirmarSalida) AlertDialog(onDismissRequest = vm::seguirEditando,
        title = { Text("¿Descartar el borrador?") },
        text = { Text("Se descartará este mantenimiento sin guardar. El kilometraje que ya hayas confirmado permanece.") },
        confirmButton = { TextButton(onClick = { vm.descartar(); onCerrar() }) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = vm::seguirEditando) { Text("Seguir editando") } })
    if (s.vehiculoPendiente != null) AlertDialog(onDismissRequest = vm::cancelarVehiculo,
        title = { Text("¿Cambiar de vehículo?") }, text = { Text("El nuevo vehículo comenzará con un formulario vacío.") },
        confirmButton = { TextButton(onClick = vm::confirmarVehiculo) { Text("Cambiar") } },
        dismissButton = { TextButton(onClick = vm::cancelarVehiculo) { Text("Conservar borrador") } })
    RepuestoMantenimientoDialog(s, vm)
}
