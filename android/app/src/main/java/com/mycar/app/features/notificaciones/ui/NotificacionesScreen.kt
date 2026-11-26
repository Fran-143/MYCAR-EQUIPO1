package com.mycar.app.features.notificaciones.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.notificaciones.domain.DetalleNotificacion
import com.mycar.app.features.notificaciones.domain.NotificacionItem
import com.mycar.app.features.notificaciones.domain.NotificacionesRepository
import com.mycar.app.features.notificaciones.domain.ResultadoLectura
import com.mycar.app.features.notificaciones.viewmodel.CategoriaNotificaciones
import com.mycar.app.features.notificaciones.viewmodel.EstadoNotificaciones
import com.mycar.app.features.notificaciones.viewmodel.FaseNotificaciones
import com.mycar.app.features.notificaciones.viewmodel.FiltroLecturaNotificaciones
import com.mycar.app.features.notificaciones.viewmodel.NotificacionesViewModel
import com.mycar.app.features.notificaciones.viewmodel.PestanaNotificaciones

private val Fondo = Color(0xFFF0F5FA)
private val Azul = Color(0xFF12285F)
private val Celeste = Color(0xFF007E98)
private val AzulPlaca = Color(0xFF0B5F8A)

@Composable
fun NotificacionesRoute(
    repository: NotificacionesRepository,
    onVolver: () -> Unit,
    onAbrirDetalleAlerta: (idVehiculo: Long, idAlerta: Long) -> Unit,
    onAbrirSoatDesdeAviso: ((idVehiculo: Long) -> Unit)? = null,
    onAbrirLicenciaDesdeAviso: (() -> Unit)? = null,
    onAbrirKilometrajeDesdeAviso: ((idVehiculo: Long) -> Unit)? = null,
    onPerfil: (() -> Unit)? = null,
    avatarBytes: ByteArray? = null,
    iniciales: String? = null,
    categoriaInicial: String? = null,
    idResaltar: Long? = null,
    modifier: Modifier = Modifier
) {
    val catInit = CategoriaNotificaciones.entries.firstOrNull {
        it.api.equals(categoriaInicial, ignoreCase = true)
    }
    val vm: NotificacionesViewModel = viewModel(key = "rf46-bandeja-${catInit?.api}-$idResaltar") {
        NotificacionesViewModel(repository, catInit, idResaltar)
    }
    val estado by vm.estado.collectAsStateWithLifecycle()
    var avisoDestino by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.recargar()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun navegarDesdeLectura(lectura: ResultadoLectura, categoria: String) {
        when {
            categoria == "KILOMETRAJE" || lectura.destino == "ACTUALIZAR_KILOMETRAJE" -> {
                val idV = lectura.idVehiculo
                if (idV != null && idV > 0) {
                    onAbrirKilometrajeDesdeAviso?.invoke(idV)
                        ?: run { avisoDestino = "No se pudo abrir el kilometraje." }
                } else avisoDestino = "No se pudo abrir el kilometraje."
            }
            categoria == "LICENCIA" -> onAbrirLicenciaDesdeAviso?.invoke()
                ?: run { avisoDestino = "No se pudo abrir la licencia." }
            categoria == "SOAT" -> {
                val idV = lectura.idVehiculo
                if (idV != null && idV > 0) {
                    onAbrirSoatDesdeAviso?.invoke(idV)
                        ?: onAbrirDetalleAlerta(idV, lectura.idAlerta ?: 0)
                } else avisoDestino = "No se pudo abrir el SOAT."
            }
            else -> {
                val idV = lectura.idVehiculo
                val idA = lectura.idAlerta
                if (idV != null && idA != null) onAbrirDetalleAlerta(idV, idA)
                else avisoDestino = "No se pudo abrir el aviso."
            }
        }
    }
    NotificacionesScreen(
        estado = estado,
        avisoDestino = avisoDestino,
        onVolver = onVolver,
        onPestana = vm::seleccionarPestana,
        onCategoria = vm::seleccionarCategoria,
        onFiltroLectura = vm::seleccionarFiltroLectura,
        onRecargar = vm::recargar,
        onAbrir = { item ->
            avisoDestino = null
            vm.abrirDetalle(item)
        },
        onCerrarDetalle = vm::cerrarDetalle,
        onAccionDetalle = {
            val cat = estado.detalle?.categoria ?: return@NotificacionesScreen
            vm.ejecutarAccionDetalle(
                onDestino = { lectura -> navegarDesdeLectura(lectura, cat) },
                onDestinoInvalido = { avisoDestino = it }
            )
        },
        onCerrarAviso = { avisoDestino = null },
        onPerfil = onPerfil,
        avatarBytes = avatarBytes,
        iniciales = iniciales,
        modifier = modifier
    )
}

@Composable
fun NotificacionesScreen(
    estado: EstadoNotificaciones,
    avisoDestino: String?,
    onVolver: () -> Unit,
    onPestana: (PestanaNotificaciones) -> Unit,
    onCategoria: (CategoriaNotificaciones) -> Unit,
    onFiltroLectura: (FiltroLecturaNotificaciones) -> Unit,
    onRecargar: () -> Unit,
    onAbrir: (NotificacionItem) -> Unit,
    onCerrarDetalle: () -> Unit = {},
    onAccionDetalle: () -> Unit = {},
    onCerrarAviso: () -> Unit,
    onPerfil: (() -> Unit)? = null,
    avatarBytes: ByteArray? = null,
    iniciales: String? = null,
    modifier: Modifier = Modifier
) {
    val detalle = estado.detalle
    if (detalle != null) {
        DetalleNotificacionModal(
            detalle = detalle,
            onCerrar = onCerrarDetalle,
            onAccion = onAccionDetalle
        )
    }
    Column(modifier.fillMaxSize().background(Fondo)) {
        com.mycar.app.ui.MycarCabeceraApp(
            onPerfil = onPerfil,
            conStatusBars = true,
            avatarBytes = avatarBytes,
            iniciales = iniciales
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onVolver, modifier = Modifier.testTag("rf46.volver")) {
                Text("← Volver", color = Azul)
            }
        }
        Text(
            "Notificaciones",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Azul
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Por gestionar: ${estado.contadorPendientesGlobal}",
                style = MaterialTheme.typography.labelLarge,
                color = Celeste,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("rf46.contadorGestion")
            )
            Text(
                "No leídas: ${estado.contadorNoLeidasGlobal}",
                style = MaterialTheme.typography.labelLarge,
                color = Azul,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("rf46.contadorNoLeidas")
            )
            Text(
                "Leídas: ${estado.contadorLeidasGlobal}",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF6881A5),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("rf46.contadorLeidas")
            )
        }
        PestanasBandeja(
            activa = estado.pestana,
            pendientes = estado.contadorPendientesGlobal,
            onCambiar = onPestana
        )
        Text("Lectura", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6881A5))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FiltroLecturaNotificaciones.entries.forEach { f ->
                val etiqueta = when (f) {
                    FiltroLecturaNotificaciones.TODAS -> f.etiqueta
                    FiltroLecturaNotificaciones.NO_LEIDAS ->
                        "${f.etiqueta} (${estado.contadorNoLeidasGlobal})"
                    FiltroLecturaNotificaciones.LEIDAS ->
                        "${f.etiqueta} (${estado.contadorLeidasGlobal})"
                }
                FilterChip(
                    selected = estado.filtroLectura == f,
                    onClick = { onFiltroLectura(f) },
                    enabled = estado.fase != FaseNotificaciones.CARGANDO,
                    label = { Text(etiqueta) },
                    modifier = Modifier.testTag("rf46.lectura.${f.api}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Celeste,
                        selectedLabelColor = Color.White,
                        containerColor = Color.White,
                        labelColor = Azul
                    )
                )
            }
        }
        Text("Categoría", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6881A5))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoriaNotificaciones.entries.forEach { cat ->
                FilterChip(
                    selected = estado.categoria == cat,
                    onClick = { onCategoria(cat) },
                    enabled = estado.fase != FaseNotificaciones.CARGANDO,
                    label = { Text(cat.etiqueta) },
                    modifier = Modifier.testTag("rf46.cat.${cat.api}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Azul,
                        selectedLabelColor = Color.White,
                        containerColor = Color.White,
                        labelColor = Azul
                    )
                )
            }
        }
        avisoDestino?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth().testTag("rf46.destinoInvalido"),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(msg, color = Azul, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onCerrarAviso) { Text("Entendido", color = Celeste) }
                }
            }
        }
        when (estado.fase) {
            FaseNotificaciones.CARGANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Celeste)
                Text("Cargando notificaciones…", color = Azul)
            }
            FaseNotificaciones.ERROR -> {
                Text(estado.mensaje ?: "No se pudieron cargar las notificaciones.", color = Azul)
                OutlinedButton(onClick = onRecargar) { Text("Reintentar") }
            }
            FaseNotificaciones.VACIO -> {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("rf46.vacio"),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Sin notificaciones", fontWeight = FontWeight.SemiBold, color = Azul)
                        Text(
                            estado.mensaje ?: "No hay avisos en esta vista.",
                            color = Color(0xFF6881A5),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            FaseNotificaciones.LISTO -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize().testTag("rf46.lista")
                ) {
                    items(estado.items, key = { it.id }) { item ->
                        NotificacionCard(
                            item = item,
                            abriendo = estado.abriendoId == item.id,
                            resaltar = estado.idResaltar == item.id,
                            onAbrir = { onAbrir(item) }
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PestanasBandeja(
    activa: PestanaNotificaciones,
    pendientes: Int,
    onCambiar: (PestanaNotificaciones) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PestanaNotificaciones.entries.forEach { p ->
            val seleccionada = p == activa
            val etiqueta = if (p == PestanaNotificaciones.PENDIENTES) {
                "${p.etiqueta} ($pendientes)"
            } else {
                p.etiqueta
            }
            OutlinedButton(
                onClick = { onCambiar(p) },
                modifier = Modifier.weight(1f).testTag("rf46.tab.${p.api}"),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                border = BorderStroke(1.dp, if (seleccionada) Celeste else Azul.copy(alpha = 0.25f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (seleccionada) Color.White else Color.Transparent,
                    contentColor = if (seleccionada) Celeste else Azul
                )
            ) {
                Text(
                    etiqueta.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (seleccionada) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificacionCard(
    item: NotificacionItem,
    abriendo: Boolean,
    resaltar: Boolean = false,
    onAbrir: () -> Unit
) {
    val lineaVehiculo = lineaVehiculo(item.placa, item.vehiculoEtiqueta)
    Card(
        onClick = onAbrir,
        enabled = !abriendo,
        modifier = Modifier.fillMaxWidth().testTag("rf46.item.${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (resaltar) Color(0xFFE8F4F8) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (resaltar) 3.dp else 1.dp),
        border = if (resaltar) BorderStroke(2.dp, Celeste) else null
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(10.dp)
                    .background(
                        if (!item.leida) Celeste else Color(0xFFB0BEC5),
                        CircleShape
                    )
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (item.leida) FontWeight.Medium else FontWeight.Bold,
                    color = Azul,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (lineaVehiculo != null) {
                    Text(
                        lineaVehiculo,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = AzulPlaca,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("rf46.placa.${item.id}")
                    )
                }
                Text(
                    item.cuerpo,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6881A5),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChipEstado(
                        if (item.leida) "Leída" else "No leída",
                        if (item.leida) Color(0xFF6881A5) else Celeste
                    )
                    ChipEstado(
                        etiquetaGestionNotificacion(item.estadoGestion),
                        Azul
                    )
                    item.estadoAlerta?.takeIf { it.isNotBlank() }?.let { estado ->
                        ChipEstado(
                            etiquetaEstadoAlertaNotif(estado),
                            colorEstadoAlertaNotif(estado)
                        )
                    }
                }
                if (abriendo) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Celeste)
                }
            }
        }
    }
}

@Composable
private fun ChipEstado(texto: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            texto,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun DetalleNotificacionModal(
    detalle: DetalleNotificacion,
    onCerrar: () -> Unit,
    onAccion: () -> Unit
) {
    val vehiculoTexto = detalle.vehiculoEtiqueta?.takeIf { it.isNotBlank() }
    Dialog(onDismissRequest = onCerrar) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rf46.detalle.modal")
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    detalle.nombreEvento ?: detalle.titulo,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Azul
                )
                if (vehiculoTexto != null) {
                    Text(
                        vehiculoTexto,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = AzulPlaca
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipEstado(detalle.categoria, Celeste)
                    ChipEstado(
                        if (detalle.leida) "Leída" else "No leída",
                        if (detalle.leida) Color(0xFF6881A5) else Celeste
                    )
                    detalle.estadoActual?.let {
                        ChipEstado(etiquetaEstadoAlertaNotif(it), colorEstadoAlertaNotif(it))
                    }
                }
                HorizontalDivider(color = Color(0xFFE3ECF4))
                BloqueDetalle("Información") {
                    detalle.fechaObjetivo?.let { FilaDetalle("Fecha objetivo", it) }
                    detalle.textoTemporal?.let { FilaDetalle("Plazo", it) }
                    detalle.textoKilometraje?.let { FilaDetalle("Kilometraje", it) }
                    detalle.kmObjetivo?.let { FilaDetalle("Objetivo", "$it km") }
                    detalle.frecuenciaKm?.let { FilaDetalle("Frecuencia", it) }
                    detalle.ultimaActualizacionKm?.let { FilaDetalle("Última actualización", it) }
                    detalle.recordatorioCorrespondienteKm?.let { FilaDetalle("Recordatorio", it) }
                }
                Text(
                    detalle.cuerpo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4A5F7A)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = onCerrar,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Azul.copy(alpha = 0.3f))
                    ) {
                        Text("Cerrar", color = Azul)
                    }
                    if (detalle.accionHabilitada && !detalle.accionDisponible.isNullOrBlank()) {
                        Button(
                            onClick = onAccion,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Celeste,
                                contentColor = Color.White
                            )
                        ) {
                            Text(detalle.accionDisponible!!)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BloqueDetalle(titulo: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            titulo,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Celeste
        )
        content()
    }
}

@Composable
private fun FilaDetalle(etiqueta: String, valor: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6881A5))
        Text(
            valor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = Azul,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false)
        )
    }
}

private fun lineaVehiculo(placa: String?, etiqueta: String?): String? {
    val p = placa?.trim()?.takeIf { it.isNotEmpty() }
    val e = etiqueta?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        p != null && e != null && !e.contains(p, ignoreCase = true) -> "$p · $e"
        p != null && e != null -> e
        p != null -> p
        e != null -> e
        else -> null
    }
}

/** Gestión de bandeja (RF45/46): no confundir con estado RF23 de la alerta. */
private fun etiquetaGestionNotificacion(estadoGestion: String): String = when (estadoGestion.uppercase()) {
    "PENDIENTE" -> "Por gestionar"
    "ATENDIDA" -> "Atendida"
    "CERRADA" -> "Cerrada"
    else -> estadoGestion.ifBlank { "—" }
}

private fun etiquetaEstadoAlertaNotif(estado: String): String = when (estado.uppercase()) {
    "PROGRAMADA" -> "Programada"
    "PROXIMA" -> "Próxima"
    "PENDIENTE" -> "Pendiente"
    "VENCIDA" -> "Vencida"
    "REALIZADA" -> "Realizada"
    "ANULADA" -> "Anulada"
    else -> estado
}

private fun colorEstadoAlertaNotif(estado: String): Color = when (estado.uppercase()) {
    "VENCIDA" -> Color(0xFFC62828)
    "PENDIENTE" -> Color(0xFFE65100)
    "PROXIMA" -> Color(0xFF007E98)
    "PROGRAMADA" -> Color(0xFF6881A5)
    else -> Azul
}
