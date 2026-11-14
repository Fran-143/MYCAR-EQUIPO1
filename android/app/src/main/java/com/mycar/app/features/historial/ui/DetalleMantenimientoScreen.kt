package com.mycar.app.features.historial.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.mycar.app.features.historial.domain.DetalleMantenimientoHistorial
import com.mycar.app.features.historial.domain.HistorialRepository
import com.mycar.app.features.historial.viewmodel.DetalleMantenimientoViewModel
import com.mycar.app.features.historial.viewmodel.EstadoDetalleMantenimiento
import com.mycar.app.features.historial.viewmodel.FaseDetalle
import com.mycar.app.features.historial.viewmodel.ModoTaller
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Fondo = Color(0xFFF0F5FA)
private val Azul = Color(0xFF12285F)
private val Celeste = Color(0xFF007E98)
private val RojoAnular = Color(0xFFC62828)
private val CampoLectura = Color(0xFFE8F4FA)
private val ChipLectura = Color(0xFFE8EEF5)

@Composable
fun DetalleMantenimientoRoute(
    repository: HistorialRepository,
    idVehiculo: Long,
    idMantenimiento: Long,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    talleresRepository: com.mycar.app.features.talleres.domain.TalleresRepository? = null
) {
    val factory = remember(repository, idVehiculo, idMantenimiento, talleresRepository) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DetalleMantenimientoViewModel(
                    repository, idVehiculo, idMantenimiento, talleresRepository
                ) as T
            }
        }
    }
    val vm: DetalleMantenimientoViewModel = viewModel(
        key = "rf19:$idVehiculo:$idMantenimiento",
        factory = factory
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    // RF21 B6: tras anulación exitosa volver al historial (onVolver ya recarga la lista).
    LaunchedEffect(estado.fase) {
        if (estado.fase == FaseDetalle.ANULADO) onVolver()
    }
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
        onModoTaller = vm::actualizarModoTaller,
        onIdTaller = vm::actualizarIdTallerSeleccionado,
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
    onModoTaller: (ModoTaller) -> Unit,
    onIdTaller: (Long?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var confirmarAnular by remember { mutableStateOf(false) }
    val editando = estado.fase == FaseDetalle.EDITANDO
    Column(modifier.fillMaxSize().background(Fondo).testTag("rf19.pantalla")) {
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onVolver, modifier = Modifier.testTag("rf19.volver")) { Text("Volver") }
                Text(
                    "Detalle de mantenimiento",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Azul
                )
                if (editando) {
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFFE2F5FF)) {
                        Text(
                            "Editando",
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Celeste,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
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
            FaseDetalle.ANULADO -> {
                // Navegación inmediata al historial (LaunchedEffect en Route); placeholder breve.
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = Celeste)
                Text("Actualizando historial…", Modifier.padding(16.dp), color = Azul)
            }
            FaseDetalle.LISTO, FaseDetalle.EDITANDO -> {
                val d = estado.detalle ?: return
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CardInfoMantenimiento(d)
                        CardEditable(
                            d = d,
                            editando = editando,
                            estado = estado,
                            onCosto = onCosto,
                            onObservaciones = onObservaciones,
                            onModoTaller = onModoTaller,
                            onIdTaller = onIdTaller
                        )
                        if (d.repuestos.isNotEmpty()) CardRepuestos(d)
                        if (d.tieneProximo) CardProximo(d)
                        if (d.bloqueadoPorAlertaActiva) {
                            Text(
                                "Hay una alerta activa que usa este mantenimiento como referencia. Debes atenderla o anularla antes de anular el registro.",
                                color = RojoAnular,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        estado.mensaje?.let {
                            Text(
                                it,
                                color = RojoAnular,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("rf20.mensaje")
                            )
                        }
                    }
                    Surface(color = Color.White, shadowElevation = 4.dp) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (editando) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = onCancelarEdicion,
                                        enabled = !estado.ocupado,
                                        modifier = Modifier.weight(1f).testTag("rf20.cancelar"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Cancelar") }
                                    Button(
                                        onClick = onGuardar,
                                        enabled = !estado.ocupado,
                                        modifier = Modifier.weight(1f).testTag("rf20.guardar"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Azul),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Guardar cambios") }
                                }
                            } else {
                                if (d.editable) {
                                    OutlinedButton(
                                        onClick = onEditar,
                                        modifier = Modifier.fillMaxWidth().testTag("rf19.editar"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Azul)
                                    ) { Text("Editar") }
                                }
                                if (d.anulable) {
                                    Button(
                                        onClick = { confirmarAnular = true },
                                        enabled = !estado.ocupado,
                                        modifier = Modifier.fillMaxWidth().testTag("rf19.anular"),
                                        colors = ButtonDefaults.buttonColors(containerColor = RojoAnular),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Anular registro") }
                                } else if (d.estado == "REALIZADO" && d.bloqueadoPorAlertaActiva) {
                                    Text("Anulación bloqueada por alerta activa", color = RojoAnular)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmarAnular) {
        DialogoAnularMantenimiento(
            onDismiss = { confirmarAnular = false },
            onConfirmar = { confirmarAnular = false; onAnular() }
        )
    }
}

@Composable
private fun CardInfoMantenimiento(d: DetalleMantenimientoHistorial) {
    SeccionCard(titulo = "Información del mantenimiento", chipSoloLectura = true) {
        CampoLectura("Vehículo", d.etiquetaVehiculo)
        CampoLectura(
            "Tipo de mantenimiento",
            if (d.codigoTipo == "OTRO") "Otro — ${d.servicioOtro}" else d.tipo
        )
        CampoLectura("Fecha del mantenimiento", d.fecha.format(fmtFecha))
        CampoLectura("Kilometraje al momento del servicio", "${fmtKm(d.kmServicio)} km")
        CampoLectura("Estado", d.estado)
        if (d.estado == "ANULADO" && !d.anuladoUtc.isNullOrBlank()) {
            CampoLectura("Anulado el", d.anuladoUtc)
        }
    }
}

@Composable
private fun CardEditable(
    d: DetalleMantenimientoHistorial,
    editando: Boolean,
    estado: EstadoDetalleMantenimiento,
    onCosto: (String) -> Unit,
    onObservaciones: (String) -> Unit,
    onModoTaller: (ModoTaller) -> Unit,
    onIdTaller: (Long?) -> Unit
) {
    SeccionCard(
        titulo = "Información editable",
        chipSoloLectura = !editando,
        chipTexto = if (editando) "Editable" else "Solo lectura"
    ) {
        if (editando) {
            // RF20: Mantener / Sin taller / Seleccionar taller (catálogo ACTIVO).
            Text(
                "Taller asociado",
                style = MaterialTheme.typography.labelLarge,
                color = Azul,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                Modifier.fillMaxWidth().testTag("rf20.taller.modos"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = estado.modoTaller == ModoTaller.MANTENER,
                    onClick = { onModoTaller(ModoTaller.MANTENER) },
                    label = { Text("Mantener") },
                    modifier = Modifier.testTag("rf20.taller.MANTENER")
                )
                FilterChip(
                    selected = estado.modoTaller == ModoTaller.SIN_TALLER,
                    onClick = { onModoTaller(ModoTaller.SIN_TALLER) },
                    label = { Text("Sin taller") },
                    modifier = Modifier.testTag("rf20.taller.SIN_TALLER")
                )
                FilterChip(
                    selected = estado.modoTaller == ModoTaller.SELECCIONAR,
                    onClick = { onModoTaller(ModoTaller.SELECCIONAR) },
                    label = { Text("Seleccionar taller") },
                    modifier = Modifier.testTag("rf20.taller.SELECCIONAR")
                )
            }
            when (estado.modoTaller) {
                ModoTaller.SELECCIONAR -> {
                    var abierto by remember { mutableStateOf(false) }
                    val sinSeleccion = estado.idTallerSeleccionado == null
                    val etiqueta = estado.talleresActivos
                        .firstOrNull { it.id == estado.idTallerSeleccionado }
                        ?.let { if (it.favorito) "★ ${it.nombre}" else it.nombre }
                        ?: "Seleccionar taller"
                    val bordeError = if (estado.errorTaller != null) {
                        BorderStroke(1.dp, RojoAnular)
                    } else {
                        null
                    }
                    Box(Modifier.fillMaxWidth().testTag("rf20.taller")) {
                        OutlinedButton(
                            onClick = { abierto = true },
                            enabled = estado.talleresActivos.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            border = bordeError,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (estado.errorTaller != null || sinSeleccion) RojoAnular else Azul
                            )
                        ) {
                            Text(
                                etiqueta,
                                Modifier.weight(1f),
                                color = if (estado.errorTaller != null) RojoAnular
                                else if (sinSeleccion) Color(0xFF6881A5)
                                else Azul
                            )
                            Text("⌄")
                        }
                        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                            estado.talleresActivos.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(if (t.favorito) "★ ${t.nombre}" else t.nombre) },
                                    onClick = {
                                        abierto = false
                                        onIdTaller(t.id)
                                    }
                                )
                            }
                        }
                    }
                    estado.errorTaller?.let { err ->
                        Text(
                            err,
                            color = RojoAnular,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("rf20.taller.error")
                        )
                    }
                    if (estado.talleresActivos.isEmpty()) {
                        Text(
                            "No hay talleres activos. Agrégalos en Historial → Talleres.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RojoAnular
                        )
                    }
                }
                ModoTaller.SIN_TALLER -> {
                    CampoLectura("Taller asociado", "Sin taller")
                    Spacer(Modifier.height(0.dp).testTag("rf20.taller"))
                }
                ModoTaller.MANTENER -> {
                    CampoLectura(
                        "Taller asociado",
                        d.taller?.takeIf { it.isNotBlank() } ?: "Sin taller"
                    )
                    Spacer(Modifier.height(0.dp).testTag("rf20.taller"))
                }
            }
            Spacer(Modifier.height(0.dp).testTag("rf20.taller.${estado.modoTaller.name}"))
            CampoEditable(
                etiqueta = "Costo",
                valor = estado.costoTexto,
                placeholder = "No registrado",
                testTag = "rf20.costo",
                onValueChange = onCosto,
                singleLine = true
            )
            CampoEditable(
                etiqueta = "Descripción / Observaciones",
                valor = estado.observaciones,
                placeholder = "—",
                testTag = "rf20.obs",
                onValueChange = onObservaciones,
                minLines = 3
            )
        } else {
            CampoLectura("Taller asociado", d.taller ?: "Sin taller")
            CampoLectura("Costo", d.costo?.let { "S/ ${it.toPlainString()}" } ?: "No registrado")
            CampoLectura("Descripción / Observaciones", d.observaciones ?: "—")
        }
    }
}

@Composable
private fun CardRepuestos(d: DetalleMantenimientoHistorial) {
    SeccionCard(titulo = "Repuestos utilizados", chipSoloLectura = true) {
        d.repuestos.forEach { r ->
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = CampoLectura
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.nombre, fontWeight = FontWeight.SemiBold, color = Azul)
                    r.marca?.takeIf { it.isNotBlank() }?.let { marca ->
                        Text(
                            "Marca: $marca",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6881A5),
                            modifier = Modifier.testTag("rf19.repuesto.marca")
                        )
                    }
                    Text(
                        "Cantidad: ${r.cantidad}" +
                            (r.precioUnitario?.let { " · Precio unitario: S/ ${it.toPlainString()}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6881A5)
                    )
                }
            }
        }
    }
}

@Composable
private fun CardProximo(d: DetalleMantenimientoHistorial) {
    SeccionCard(titulo = "Próximo mantenimiento", chipSoloLectura = true) {
        CampoLectura(
            "Próxima fecha recomendada",
            d.proximaFechaSugerida?.format(fmtFecha) ?: "—"
        )
        CampoLectura(
            "Próximo kilometraje recomendado",
            d.proximoKmSugerido?.let { "${fmtKm(it)} km" } ?: "—"
        )
    }
}

@Composable
private fun SeccionCard(
    titulo: String,
    chipSoloLectura: Boolean,
    chipTexto: String = if (chipSoloLectura) "Solo lectura" else "",
    contenido: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(titulo, fontWeight = FontWeight.Bold, color = Celeste, style = MaterialTheme.typography.titleMedium)
                if (chipTexto.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (chipSoloLectura) ChipLectura else Color(0xFFE2F5FF)
                    ) {
                        Text(
                            chipTexto,
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chipSoloLectura) Azul else Celeste
                        )
                    }
                }
            }
            contenido()
        }
    }
}

@Composable
private fun CampoLectura(etiqueta: String, valor: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = Color(0xFF6881A5))
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = CampoLectura
        ) {
            Text(valor, Modifier.padding(horizontal = 12.dp, vertical = 12.dp), color = Azul)
        }
    }
}

/** Misma apariencia que CampoLectura; el valor se edita sin cambiar el diseño de la card. */
@Composable
private fun CampoEditable(
    etiqueta: String,
    valor: String,
    placeholder: String,
    testTag: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false,
    minLines: Int = 1
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = Color(0xFF6881A5))
        TextField(
            value = valor,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .background(CampoLectura, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            textStyle = LocalTextStyle.current.copy(color = Azul),
            placeholder = { Text(placeholder, color = Color(0xFF6881A5)) },
            singleLine = singleLine,
            minLines = minLines,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = Celeste
            )
        )
    }
}

@Composable
private fun DialogoAnularMantenimiento(onDismiss: () -> Unit, onConfirmar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Surface(shape = CircleShape, color = Color(0xFFFCE4E4)) {
                Text("✕", Modifier.padding(16.dp), color = RojoAnular, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Text("¿Anular mantenimiento?", fontWeight = FontWeight.Bold, color = Azul)
        },
        text = {
            Text(
                "Este mantenimiento quedará anulado, pero sus datos históricos y repuestos se conservarán. " +
                    "La operación no podrá completarse si existen alertas activas que utilicen este mantenimiento como referencia."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                modifier = Modifier.testTag("rf21.confirmar"),
                colors = ButtonDefaults.buttonColors(containerColor = RojoAnular),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Anular registro") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("rf21.cancelar"),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Cancelar") }
        }
    )
}

private val fmtFecha = DateTimeFormatter.ofPattern("dd/MM/uuuu")
private fun fmtKm(km: Int): String =
    DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(km).replace(',', ' ')
