package com.mycar.app.features.mantenimiento.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mycar.app.features.mantenimiento.viewmodel.MantenimientoUiState
import com.mycar.app.features.mantenimiento.viewmodel.MantenimientoViewModel
import com.mycar.app.ui.theme.MyCarBlue
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarSky

@Composable
internal fun RegistrarMantenimientoScreen(
    s: MantenimientoUiState, vm: MantenimientoViewModel, modifier: Modifier,
    onCerrar: () -> Unit, onCerrarConfirmado: () -> Unit,
    onActualizarKm: ((Long) -> Unit)?, onSesionRequerida: (() -> Unit)?,
    onProgramarAlerta: ((com.mycar.app.features.mantenimiento.viewmodel.OfertaProgramarAlerta) -> Unit)? = null
) {
    val acciones = remember(vm) { AccionesDesdeViewModel(vm) }
    FormularioRegistroMantenimiento(s, acciones, modifier, onCerrar, onCerrarConfirmado,
        onActualizarKm, onSesionRequerida, onProgramarAlerta)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormularioRegistroMantenimiento(
    s: MantenimientoUiState, vm: AccionesMantenimiento, modifier: Modifier,
    onCerrar: () -> Unit, onCerrarConfirmado: () -> Unit,
    onActualizarKm: ((Long) -> Unit)?, onSesionRequerida: (() -> Unit)?,
    onProgramarAlerta: ((com.mycar.app.features.mantenimiento.viewmodel.OfertaProgramarAlerta) -> Unit)? = null
) {
    val b = s.borrador
    val vehiculo = s.contexto?.vehiculos?.firstOrNull { it.idVehiculo == b.idVehiculo }
    val enabled = s.editable && !s.cargando
    s.resultado?.let {
        ResultadoMantenimientoScreen(
            r = it,
            modifier = modifier,
            oferta = if (s.modoDesdeAlerta) null else s.ofertaProgramarAlerta,
            atencionDesdeAlerta = s.modoDesdeAlerta,
            onVolver = onCerrarConfirmado,
            onProgramarAlerta = { oferta ->
                onProgramarAlerta?.invoke(oferta) ?: onCerrarConfirmado()
            }
        )
        return
    }
    val scheme = MaterialTheme.colorScheme
    val oscuro = false // Tema fijo claro MyCar
    val tituloForm = if (s.modoDesdeAlerta) "Registrar como realizado" else "Registrar mantenimiento"
    val subtitulo = if (s.modoDesdeAlerta)
        "Completa el servicio para marcar la alerta como REALIZADA. Se creará un mantenimiento nuevo."
    else
        "Cuida el historial de tu vehículo. Registra un servicio que ya realizaste."
    val pagina = if (oscuro) scheme.background else scheme.surfaceVariant
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = pagina,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CabeceraRegistro(
                titulo = tituloForm,
                subtitulo = subtitulo,
                enviando = s.enviando,
                onVolver = {
                    when {
                        s.requiereSesion || s.incierto -> onCerrar()
                        else -> vm.solicitarSalir()
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = scheme.surface) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(color = scheme.outlineVariant)
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val hayRecomendacion = !s.modoDesdeAlerta && (b.proximaFecha.isNotBlank() || b.proximoKm.isNotBlank())
                        val puedeGuardar = enabled && s.contexto?.registroDisponible == true
                        val primario = ButtonDefaults.buttonColors(
                            containerColor = scheme.primary,
                            contentColor = scheme.onPrimary
                        )
                        if (hayRecomendacion) {
                            Button(
                                onClick = { vm.guardar(programarAlerta = true) },
                                enabled = puedeGuardar,
                                modifier = Modifier.fillMaxWidth().testTag("rf17.guardar.alerta"),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(14.dp),
                                colors = primario
                            ) {
                                Text(if (s.enviando && s.programarAlertaTrasExito) "Guardando…" else "Sí, programar alerta")
                            }
                            OutlinedButton(
                                onClick = { vm.guardar(programarAlerta = false) },
                                enabled = puedeGuardar && !s.enviando,
                                modifier = Modifier.fillMaxWidth().testTag("rf17.guardar"),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.primary)
                            ) {
                                Text(if (s.enviando && !s.programarAlertaTrasExito) "Guardando…" else "No, solo guardar")
                            }
                        } else {
                            Button(
                                onClick = { vm.guardar(programarAlerta = false) },
                                enabled = puedeGuardar,
                                modifier = Modifier.fillMaxWidth().testTag("rf17.guardar"),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(14.dp),
                                colors = primario
                            ) {
                                Text(
                                    when {
                                        s.enviando && s.modoDesdeAlerta -> "Registrando…"
                                        s.enviando -> "Guardando…"
                                        s.modoDesdeAlerta -> "Registrar y marcar alerta"
                                        else -> "Guardar mantenimiento"
                                    }
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { if (s.requiereSesion) onCerrar() else vm.solicitarSalir() },
                            enabled = !s.enviando && !s.incierto,
                            modifier = Modifier.fillMaxWidth().testTag("rf17.cancelar"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.primary)
                        ) { Text("Cancelar") }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (s.requiereSesion) {
                AvisoMantenimiento("Inicia sesión para elegir tu vehículo y registrar un mantenimiento.")
                onSesionRequerida?.let { TextButton(onClick = it) { Text("Iniciar sesión") } }
            }
            if (s.cargando || s.enviando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("rf17.cargando"), color = scheme.primary)
                Text(if (s.enviando) "Guardando el mantenimiento…" else "Cargando tus vehículos y tipos de mantenimiento…",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            s.mensaje?.let { AvisoMantenimiento(it, error = true) }
            if (s.errores.isNotEmpty()) AvisoMantenimiento("Revisa los campos marcados antes de guardar.", error = true)
            if (s.incierto) {
                OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Resultado pendiente de verificar", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                        Text("Conservamos tu solicitud. Verifica el resultado antes de registrar otra vez este servicio.", color = scheme.onSurface)
                        Button(onClick = vm::consultarOperacion, enabled = !s.enviando,
                            modifier = Modifier.testTag("rf17.verificar"),
                            colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)
                        ) { Text("Verificar resultado") }
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
            SeccionMantenimiento("1", "Información obligatoria", icono = Icons.Outlined.DirectionsCar) {
                SelectorMantenimiento("Vehículo *", vehiculo?.let { "${it.placa} · ${it.marca} ${it.modelo}" } ?: "Seleccionar vehículo",
                    s.contexto?.vehiculos.orEmpty().map { it.idVehiculo to "${it.placa} · ${it.marca} ${it.modelo}" },
                    enabled && !s.modoDesdeAlerta, s.errores["idVehiculo"], icono = Icons.Outlined.DirectionsCar,
                    onSelect = vm::solicitarVehiculo)
                if (s.modoDesdeAlerta) {
                    Text("Vehículo fijado por la alerta (solo lectura).", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (s.contexto == null && !s.cargando) {
                    Text("No se pudieron cargar tus vehículos. Pulsa «Actualizar referencias».",
                        style = MaterialTheme.typography.bodySmall, color = scheme.error)
                } else if (s.contexto?.vehiculos?.isEmpty() == true) {
                    Text("No tienes vehículos activos disponibles.", color = scheme.onSurface)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.primaryContainer
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Outlined.Speed,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 2.dp).size(22.dp)
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Kilometraje vigente", style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer)
                            Text(
                                vehiculo?.kmActual?.let { "$it km" } ?: "Sin lectura",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onPrimaryContainer
                            )
                            vehiculo?.fechaLecturaActual?.let {
                                Text(fechaLecturaVisible(it), style = MaterialTheme.typography.bodySmall, color = scheme.onPrimaryContainer)
                            }
                            Text(
                                "Aceite registrado del vehículo: ${vehiculo?.aceiteRegistrado ?: "No informado"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onPrimaryContainer
                            )
                        }
                    }
                }
                if (b.codigoTipo == "CAMBIO_ACEITE") {
                    val aceite = vehiculo?.aceiteRegistrado
                    if (!aceite.isNullOrBlank() && !aceite.equals("No informado", true)) {
                        Text("Al elegir Cambio de aceite se propone «$aceite» como repuesto (editable).",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        OutlinedButton(onClick = vm::proponerAceiteVehiculo, enabled = enabled) {
                            Text("Volver a proponer aceite del vehículo")
                        }
                    } else {
                        Text("Este vehículo no tiene tipo de aceite registrado; indícalo en el repuesto si aplica.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                val talleres = s.talleresActivos
                val etiquetaTaller = when {
                    b.idTaller == null -> "Sin taller"
                    else -> talleres.firstOrNull { it.id == b.idTaller }?.nombre ?: "Taller #${b.idTaller}"
                }
                val opcionesTaller = listOf(null to "Sin taller") +
                    talleres.map { it.id as Long? to (if (it.favorito) "★ ${it.nombre}" else it.nombre) }
                SelectorMantenimiento(
                    "Taller asociado",
                    etiquetaTaller,
                    opcionesTaller.map { (id, nom) -> (id?.toString() ?: "") to nom },
                    enabled,
                    s.errores["idTaller"],
                    Modifier.testTag("rf17.taller"),
                    icono = Icons.Outlined.Home
                ) { codigo ->
                    vm.actualizar { it.copy(idTaller = codigo.toLongOrNull()) }
                }
                if (!s.requiereSesion && !s.modoDesdeAlerta) {
                    OutlinedButton(
                        onClick = { b.idVehiculo?.let { onActualizarKm?.invoke(it) } },
                        enabled = enabled && b.idVehiculo != null && onActualizarKm != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.primary)
                    ) {
                        Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Actualizar kilometraje vigente")
                    }
                    if (onActualizarKm == null) Text("La actualización del kilometraje aún no está disponible desde esta pantalla.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (s.modoDesdeAlerta) {
                    Text("El km del servicio usa lecturas históricas (no crea una lectura RF14).",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            SeccionMantenimiento("2", "Detalle del servicio", "Campos con * obligatorios.", icono = Icons.Outlined.Build) {
                SelectorMantenimiento("Tipo de mantenimiento *",
                    s.contexto?.tipos?.firstOrNull { it.codigo == b.codigoTipo }?.nombre ?: b.codigoTipo.ifBlank { "Seleccionar tipo" },
                    s.contexto?.tipos.orEmpty().map { it.codigo to it.nombre }, enabled && !s.modoDesdeAlerta,
                    s.errores["codigoTipoMantenimiento"], Modifier.testTag("rf17.tipo"),
                    icono = Icons.Outlined.Build) { codigo ->
                    vm.actualizar { it.copy(codigoTipo = codigo, servicioOtro = if (codigo == "OTRO") it.servicioOtro else "") }
                }
                if (s.modoDesdeAlerta) {
                    Text("Tipo fijado por la alerta (solo lectura).", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (b.codigoTipo == "OTRO") CampoMantenimiento("Servicio específico *", b.servicioOtro, enabled && !s.modoDesdeAlerta,
                    s.errores["servicioOtro"], modifier = Modifier.testTag("rf17.servicioOtro"),
                    icono = Icons.Outlined.Edit) {
                    v -> vm.actualizar { it.copy(servicioOtro = v) }
                }
                FechaMantenimiento("Fecha del servicio *", b.fecha, enabled, s.contexto?.fechaServidor,
                    vm::fecha, error = s.errores["fechaServicio"])
                SelectorMantenimiento("Origen del kilometraje *",
                    if (b.origen == "LECTURA") "Lectura existente" else "Kilometraje histórico declarado",
                    listOf("LECTURA" to "Lectura existente", "HISTORICO_MANUAL" to "Kilometraje histórico declarado"),
                    enabled, s.errores["origenKmServicio"], icono = Icons.Outlined.Speed, onSelect = vm::origen)
                if (b.origen == "LECTURA") {
                    if (s.cargandoLecturas) Text("Cargando lecturas…", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    SelectorMantenimiento("Lectura para el servicio *",
                        s.lecturas.firstOrNull { it.idLecturaKm == b.lectura }?.let { "${it.valorKm} km · ${fechaLecturaVisible(it.fechaLecturaUtc)}" }
                            ?: "Seleccionar lectura",
                        s.lecturas.map { it.idLecturaKm to "${it.valorKm} km · ${fechaLecturaVisible(it.fechaLecturaUtc)}" },
                        enabled && !s.cargandoLecturas, s.errores["idLecturaKmReferencia"],
                        icono = Icons.Outlined.Speed, onSelect = vm::lectura)
                    if (!s.requiereSesion && !s.cargandoLecturas && s.lecturas.isEmpty())
                        Text("No hay lecturas para esa fecha. Puedes declarar el kilometraje histórico.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                CampoMantenimiento("Kilometraje real del servicio *", b.km, enabled && b.origen == "HISTORICO_MANUAL",
                    s.errores["kmServicio"], KeyboardType.Number, modifier = Modifier.testTag("rf17.km"),
                    icono = Icons.Outlined.Speed) {
                    v -> vm.actualizar { it.copy(km = v) }
                }
                Text("Corresponde al servicio realizado. No modifica el kilometraje vigente.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            SeccionMantenimiento("3", "Información adicional", "Opcional.", icono = Icons.Outlined.Payments) {
                CampoMantenimiento("Costo total", b.costo, enabled, s.errores["costoTotal"], KeyboardType.Decimal,
                    icono = Icons.Outlined.Payments) {
                    v -> vm.actualizar { it.copy(costo = v) }
                }
                Text("Puedes dejarlo vacío o indicar 0. Usa hasta dos decimales, sin separador de miles.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                CampoMantenimiento("Descripción u observaciones", b.observaciones, enabled,
                    s.errores["observaciones"], singleLine = false, icono = Icons.Outlined.Edit) {
                    v -> vm.actualizar { it.copy(observaciones = v) }
                }
            }
            SeccionMantenimiento("4", "Repuestos utilizados (opcional)", "Añade repuestos si los hubo.", icono = Icons.Outlined.Inventory2) {
                if (b.repuestos.isEmpty()) Text("Aún no agregaste repuestos.", style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant)
                b.repuestos.forEach { p ->
                    key(p.idLocal) {
                        Surface(shape = RoundedCornerShape(14.dp), color = scheme.primaryContainer) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Inventory2,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(p.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                                    Text("${p.cantidad} unidad(es) · ${p.marca.ifBlank { "Marca no informada" }}",
                                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                                    Text("Precio unitario: ${p.precio.ifBlank { "No informado" }}",
                                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                                }
                                FilledTonalIconButton(
                                    onClick = { vm.editarRepuesto(p.idLocal) },
                                    enabled = enabled,
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Editar", modifier = Modifier.size(20.dp))
                                }
                                FilledTonalIconButton(
                                    onClick = { vm.quitarRepuesto(p.idLocal) },
                                    enabled = enabled,
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = scheme.errorContainer,
                                        contentColor = scheme.error,
                                        disabledContainerColor = scheme.surfaceVariant,
                                        disabledContentColor = scheme.onSurface.copy(alpha = 0.38f)
                                    )
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Quitar", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { vm.editarRepuesto() },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("rf17.agregarRepuesto"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.primary)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar repuesto")
                }
                s.errores.filterKeys { it.startsWith("repuestos[") }.values.distinct().forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error)
                }
            }
            if (!s.modoDesdeAlerta) {
                SeccionMantenimiento("5", "Próximo mantenimiento (opcional)", "Se conserva como referencia.", icono = Icons.Outlined.CalendarMonth) {
                    FechaMantenimiento("Próxima fecha sugerida", b.proximaFecha, enabled, null,
                        { v -> vm.actualizar { it.copy(proximaFecha = v) } }, opcional = true,
                        error = s.errores["proximaFechaSugerida"])
                    CampoMantenimiento("Próximo kilometraje sugerido", b.proximoKm, enabled,
                        s.errores["proximoKmSugerido"], KeyboardType.Number, icono = Icons.Outlined.Speed) {
                        v -> vm.actualizar { it.copy(proximoKm = v) }
                    }
                    if (b.proximaFecha.isNotBlank() || b.proximoKm.isNotBlank()) {
                        Text("Puedes guardar y programar una alerta preventiva con esta recomendación.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (s.confirmarSalida) {
        com.mycar.app.ui.CambiosSinGuardarDialog(
            onSeguirEditando = vm::seguirEditando,
            onDescartar = { vm.descartar(); onCerrar() },
            mensaje = "Tienes cambios que aún no has guardado. Si sales, se perderán."
        )
    }
    if (s.vehiculoPendiente != null) AlertDialog(onDismissRequest = vm::cancelarVehiculo,
        title = { Text("¿Cambiar de vehículo?") }, text = { Text("El nuevo vehículo comenzará con un formulario vacío.") },
        confirmButton = { TextButton(onClick = vm::confirmarVehiculo) { Text("Cambiar") } },
        dismissButton = { TextButton(onClick = vm::cancelarVehiculo) { Text("Conservar borrador") } })
    RepuestoMantenimientoDialog(s, vm)
}

@Composable
private fun CabeceraRegistro(
    titulo: String,
    subtitulo: String,
    enviando: Boolean,
    onVolver: () -> Unit
) {
    val oscuro = false // Tema fijo claro MyCar
    val brush = Brush.verticalGradient(
        if (oscuro) listOf(MyCarNavy, Color(0xFF000000))
        else listOf(MyCarNavy, MyCarBlue, MyCarSky)
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(brush)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 20.dp, bottom = 20.dp)
    ) {
        IconButton(
            onClick = onVolver,
            enabled = !enviando,
            modifier = Modifier.testTag("rf17.volver")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
        }
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.16f)) {
                Icon(
                    Icons.Outlined.Build,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp).size(28.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    subtitulo,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }
    }
}
