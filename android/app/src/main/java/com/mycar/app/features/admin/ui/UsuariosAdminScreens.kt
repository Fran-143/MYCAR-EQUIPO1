@file:OptIn(ExperimentalMaterial3Api::class)

package com.mycar.app.features.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mycar.app.BuildConfig
import com.mycar.app.core.FechaMyCar
import com.mycar.app.features.admin.data.AdminSuscripcionesRepository
import com.mycar.app.features.admin.data.AdminUsuarioDetalleDto
import com.mycar.app.features.admin.data.AdminUsuarioResumenDto
import com.mycar.app.features.admin.data.AdminUsuariosRepository
import com.mycar.app.features.localtest.CodigoPruebaLocalBanner
import com.mycar.app.features.localtest.consultarOtpSimulado
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.unit.sp

private val Teal = Color(0xFF007E98)
private val Danger = Color(0xFFC53030)
private val Green = Color(0xFF2F855A)
private val Orange = Color(0xFFDD6B20)
private val Amber = Color(0xFFD69E2E)

private enum class UsuariosPaso {
    LISTA, DETALLE, HISTORIAL, GESTION, SUSPENDER, BLOQUEAR, DESBLOQUEAR, REACTIVAR, ASISTENCIA
}

@Composable
fun UsuariosAdminRoute(
    repository: AdminUsuariosRepository,
    suscripcionesRepository: AdminSuscripcionesRepository? = null,
    onVolver: () -> Unit,
    onAbrirPruebas: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var paso by remember { mutableStateOf(UsuariosPaso.LISTA) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var detalle by remember { mutableStateOf<AdminUsuarioDetalleDto?>(null) }
    var detalleError by remember { mutableStateOf<String?>(null) }
    var detalleLoading by remember { mutableStateOf(false) }
    var bandejaDest by remember { mutableStateOf<String?>(null) }
    var bandejaFin by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (bandejaDest != null || bandejaFin != null) {
        com.mycar.app.features.localtest.PruebasLocalesRoute(
            onVolver = { bandejaDest = null; bandejaFin = null },
            destinatarioInicial = bandejaDest,
            finalidadInicial = bandejaFin,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    fun abrirDetalle(id: Long) {
        selectedId = id
        detalleError = null
        detalleLoading = true
        paso = UsuariosPaso.DETALLE
        scope.launch {
            try {
                detalle = repository.detalle(id)
                detalleError = null
            } catch (ex: Exception) {
                detalle = null
                detalleError = ex.message ?: "No se pudo cargar el detalle."
            } finally {
                detalleLoading = false
            }
        }
    }

    fun refrescarDetalle(id: Long) {
        scope.launch {
            try {
                detalle = repository.detalle(id)
                detalleError = null
            } catch (ex: Exception) {
                detalleError = ex.message ?: "No se pudo actualizar el detalle."
            }
        }
    }

    BackHandler {
        when (paso) {
            UsuariosPaso.LISTA -> onVolver()
            UsuariosPaso.DETALLE -> paso = UsuariosPaso.LISTA
            UsuariosPaso.HISTORIAL, UsuariosPaso.GESTION -> paso = UsuariosPaso.DETALLE
            else -> paso = UsuariosPaso.GESTION
        }
    }

    when (paso) {
        UsuariosPaso.LISTA -> UsuariosListaScreen(
            repository = repository,
            onVolver = onVolver,
            onDetalle = { abrirDetalle(it) },
            modifier = modifier.fillMaxSize()
        )
        UsuariosPaso.DETALLE -> when {
            detalleLoading -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Teal)
            }
            detalleError != null -> DetalleErrorScreen(
                mensaje = detalleError!!,
                onReintentar = { selectedId?.let { abrirDetalle(it) } },
                onVolver = { paso = UsuariosPaso.LISTA },
                modifier = modifier.fillMaxSize()
            )
            detalle != null -> UsuariosDetalleScreen(
                detalle = detalle!!,
                suscripcionesRepository = suscripcionesRepository,
                onRefresh = { selectedId?.let { refrescarDetalle(it) } },
                onVolver = { paso = UsuariosPaso.LISTA },
                onHistorial = { paso = UsuariosPaso.HISTORIAL },
                onGestionar = { paso = UsuariosPaso.GESTION },
                modifier = modifier.fillMaxSize()
            )
        }
        UsuariosPaso.HISTORIAL -> selectedId?.let { id ->
            UsuariosHistorialScreen(repository, id, onVolver = { paso = UsuariosPaso.DETALLE }, modifier.fillMaxSize())
        }
        UsuariosPaso.GESTION -> detalle?.let { d ->
            UsuariosGestionScreen(
                detalle = d,
                onVolver = { paso = UsuariosPaso.DETALLE },
                onSuspender = { paso = UsuariosPaso.SUSPENDER },
                onBloquear = { paso = UsuariosPaso.BLOQUEAR },
                onDesbloquear = { paso = UsuariosPaso.DESBLOQUEAR },
                onReactivar = { paso = UsuariosPaso.REACTIVAR },
                onAsistencia = { paso = UsuariosPaso.ASISTENCIA },
                onHistorial = { paso = UsuariosPaso.HISTORIAL },
                modifier = modifier.fillMaxSize()
            )
        }
        UsuariosPaso.SUSPENDER -> selectedId?.let { id ->
            FormMotivoScreen(
                titulo = "Suspender cuenta",
                accion = "Suspender cuenta",
                peligro = true,
                onCancel = { paso = UsuariosPaso.GESTION },
                onConfirm = { motivo, comentario ->
                    repository.suspender(id, motivo, comentario)
                    refrescarDetalle(id)
                    paso = UsuariosPaso.DETALLE
                },
                modifier = modifier.fillMaxSize()
            )
        }
        UsuariosPaso.REACTIVAR -> selectedId?.let { id ->
            FormMotivoScreen(
                titulo = "Levantar suspensión",
                accion = "Levantar suspensión",
                peligro = false,
                onCancel = { paso = UsuariosPaso.GESTION },
                onConfirm = { motivo, _ ->
                    repository.reactivar(id, motivo)
                    refrescarDetalle(id)
                    paso = UsuariosPaso.DETALLE
                },
                modifier = modifier.fillMaxSize()
            )
        }
        UsuariosPaso.BLOQUEAR -> selectedId?.let { id ->
            FormBloquearScreen(
                onCancel = { paso = UsuariosPaso.GESTION },
                onConfirm = { motivo, mins, obs ->
                    repository.bloquear(id, motivo, mins, obs)
                    refrescarDetalle(id)
                    paso = UsuariosPaso.DETALLE
                },
                modifier = modifier.fillMaxSize()
            )
        }
        UsuariosPaso.DESBLOQUEAR -> selectedId?.let { id ->
            FormMotivoScreen(
                titulo = "Desbloquear cuenta",
                accion = "Desbloquear",
                peligro = false,
                onCancel = { paso = UsuariosPaso.GESTION },
                onConfirm = { motivo, _ ->
                    repository.desbloquear(id, motivo)
                    refrescarDetalle(id)
                    paso = UsuariosPaso.DETALLE
                },
                modifier = modifier.fillMaxSize()
            )
        }
        UsuariosPaso.ASISTENCIA -> selectedId?.let { id ->
            ReactivarAsistidaScreen(
                repository = repository,
                id = id,
                detalle = detalle,
                onVolver = { paso = UsuariosPaso.GESTION },
                onHecho = {
                    refrescarDetalle(id)
                    paso = UsuariosPaso.DETALLE
                },
                onConsultarCodigo = if (com.mycar.app.BuildConfig.LOCAL_TEST_TOOLS) {
                    {
                        bandejaDest = detalle?.correo
                        bandejaFin = "VERIF_REACTIV_ADMIN"
                    }
                } else null,
                onAbrirPruebas = onAbrirPruebas,
                modifier = modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun DetalleErrorScreen(
    mensaje: String,
    onReintentar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Detalle de usuario") },
                navigationIcon = { TextButton(onClick = onVolver) { Text("←") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text("No se pudo abrir el detalle", fontWeight = FontWeight.Bold, color = scheme.onSurface)
            Text(mensaje, color = Danger)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onReintentar,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Reintentar") }
            OutlinedButton(onClick = onVolver, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Volver a la lista")
            }
        }
    }
}

@Composable
private fun UsuariosListaScreen(
    repository: AdminUsuariosRepository,
    onVolver: () -> Unit,
    onDetalle: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf("TODOS") }
    var rol by remember { mutableStateOf("TODOS") }
    var page by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<AdminUsuarioResumenDto>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var requestSeq by remember { mutableIntStateOf(0) }

    fun cargar(resetPage: Boolean = false) {
        if (resetPage) page = 0
        val seq = ++requestSeq
        val qSnap = q
        val estadoSnap = estado
        val rolSnap = rol
        val pageSnap = if (resetPage) 0 else page
        loadJob?.cancel()
        loadJob = scope.launch {
            loading = true
            error = null
            try {
                val res = repository.listar(
                    qSnap.ifBlank { null },
                    if (estadoSnap == "TODOS") null else estadoSnap,
                    if (rolSnap == "TODOS") null else rolSnap,
                    pageSnap
                )
                if (seq != requestSeq) return@launch
                items = res.items
                total = res.total
                page = pageSnap
            } catch (ex: Exception) {
                if (seq != requestSeq) return@launch
                error = ex.message
            } finally {
                if (seq == requestSeq) loading = false
            }
        }
    }

    // Búsqueda con debounce ~350 ms; estado/rol también disparan recarga (misma corrutina cancela obsoletas).
    LaunchedEffect(q, estado, rol) {
        delay(350)
        cargar(resetPage = true)
    }

    // Causa del teclado deformado: safeDrawing (incluye IME) + imePadding + navigationBars
    // reducían la altura dos/tres veces con edge-to-edge + adjustResize.
    // Solución: solo systemBars en Scaffold; búsqueda fija; LazyColumn ocupa el resto.
    Scaffold(
        modifier = modifier,
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.People, contentDescription = null, tint = scheme.onSurface)
                        Spacer(Modifier.width(8.dp))
                        Text("Usuarios", fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = { TextButton(onClick = onVolver) { Text("←") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                label = { Text("Buscar por nombre o correo") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = scheme.onSurfaceVariant) },
                trailingIcon = {
                    if (q.isNotEmpty()) {
                        TextButton(onClick = { q = "" }) { Text("✕") }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    FiltroChip(
                        "Estado",
                        estado,
                        listOf("TODOS", "ACTIVA", "SUSPENDIDA", "BLOQUEADA", "PENDIENTE_ELIMINACION", "ELIMINADA"),
                        Icons.Outlined.FilterList
                    ) { estado = it }
                }
                Box(Modifier.weight(1f)) {
                    FiltroChip("Rol", rol, listOf("TODOS", "USUARIO", "ADMIN"), Icons.Outlined.Badge) { rol = it }
                }
            }
            TextButton(
                onClick = { q = ""; estado = "TODOS"; rol = "TODOS" },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Limpiar filtros") }
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = Teal
                )
            }
            when {
                error != null -> Text(error!!, color = Danger, modifier = Modifier.padding(8.dp))
                !loading && items.isEmpty() -> Text(
                    "No hay usuarios con esos filtros.",
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(items, key = { it.idUsuario }) { u ->
                        UsuarioFila(u) { onDetalle(u.idUsuario) }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { if (page > 0) { page--; cargar() } },
                                enabled = page > 0 && !loading
                            ) { Text("Anterior") }
                            Text("Pág. ${page + 1} · $total", color = scheme.onSurfaceVariant)
                            TextButton(
                                onClick = {
                                    if ((page + 1) * 20 < total) {
                                        page++
                                        cargar()
                                    }
                                },
                                enabled = (page + 1) * 20 < total && !loading
                            ) { Text("Siguiente") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltroChip(
    label: String,
    value: String,
    options: List<String>,
    icon: ImageVector,
    onSelect: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = scheme.secondary)
            Spacer(Modifier.width(6.dp))
            Text(
                "$label: ${value.replace('_', ' ')}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.replace('_', ' ')) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun UsuarioFila(u: AdminUsuarioResumenDto, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val visible = (u.estadoVisible ?: u.estado).orEmpty()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(scheme.secondary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.People, contentDescription = null, tint = scheme.secondary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (u.anonima) "Constancia ${u.constancia.orEmpty()}"
                    else u.nombre.orEmpty(),
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!u.anonima) {
                    Text(
                        u.correo.orEmpty(),
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                EstadoChip(visible)
                u.desbloqueoUtc?.let {
                    Text(
                        "Hasta: ${FechaMyCar.fechaHora(it)}",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Icon(Icons.Outlined.PersonSearch, contentDescription = "Abrir detalle", tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EstadoChip(estado: String) {
    val color = estadoColor(estado)
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(estadoIcon(estado), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                estado.replace('_', ' '),
                color = color,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun estadoColor(estado: String): Color {
    val scheme = MaterialTheme.colorScheme
    return when (estado) {
        "ACTIVA" -> Green
        "SUSPENDIDA" -> Danger
        "BLOQUEADA" -> Orange
        "PENDIENTE_ELIMINACION" -> Amber
        "ELIMINADA" -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }
}

private fun estadoIcon(estado: String): ImageVector = when (estado) {
    "ACTIVA" -> Icons.Outlined.CheckCircle
    "SUSPENDIDA" -> Icons.Outlined.PauseCircle
    "BLOQUEADA" -> Icons.Outlined.Lock
    "PENDIENTE_ELIMINACION" -> Icons.Outlined.History
    else -> Icons.Outlined.Info
}

@Composable
private fun UsuariosDetalleScreen(
    detalle: AdminUsuarioDetalleDto,
    suscripcionesRepository: AdminSuscripcionesRepository? = null,
    onRefresh: () -> Unit = {},
    onVolver: () -> Unit,
    onHistorial: () -> Unit,
    onGestionar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        modifier = modifier,
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Detalle de usuario") },
                navigationIcon = { TextButton(onClick = onVolver) { Text("←") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (detalle.anonima) "Constancia ${detalle.constancia.orEmpty()}"
                        else detalle.nombreCompleto ?: listOfNotNull(detalle.nombre, detalle.apellido).joinToString(" "),
                        fontWeight = FontWeight.Bold, color = scheme.onSurface
                    )
                    if (!detalle.anonima) Text(detalle.correo.orEmpty(), color = scheme.onSurfaceVariant)
                    Text(
                        (detalle.estadoVisible ?: detalle.estado).orEmpty().replace('_', ' '),
                        color = estadoColor(detalle.estadoVisible ?: detalle.estado.orEmpty()),
                        fontWeight = FontWeight.SemiBold
                    )
                    detalle.tipoBloqueoVisible?.let { Text("Tipo: $it", color = scheme.onSurfaceVariant) }
                    detalle.restriccion?.let { Text("Restricción: $it", color = scheme.onSurfaceVariant) }
                }
            }
            Spacer(Modifier.height(12.dp))
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                contentColor = scheme.onSurface
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Información") },
                    icon = { Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Vehículos") },
                    icon = { Icon(Icons.Outlined.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text("Suscripciones") },
                    icon = { Icon(Icons.Outlined.Badge, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                0 -> {
                    if (detalle.anonima) {
                        Text("Solo constancia técnica no identificable.", color = scheme.onSurfaceVariant)
                    } else {
                        InfoLine("ID administrativo", detalle.idUsuario.toString())
                        InfoLine("Nombre completo", detalle.nombreCompleto)
                        InfoLine("Correo", detalle.correo)
                        InfoLine("Rol", detalle.rol)
                        InfoLine("Estado", (detalle.estadoVisible ?: detalle.estado)?.replace('_', ' '))
                        InfoLine("Registro", FechaMyCar.fechaHora(detalle.fechaRegistroUtc))
                        InfoLine("Último acceso", FechaMyCar.fechaHora(detalle.ultimoAccesoUtc))
                        InfoLine("Motivo", detalle.motivoEstado)
                        InfoLine("Desbloqueo admin", FechaMyCar.fechaHora(detalle.bloqueoAdminHastaUtc))
                        InfoLine("Eliminación programada", FechaMyCar.fecha(detalle.eliminacionProgramadaUtc))
                    }
                }
                1 -> {
                    val v = detalle.vehiculos
                    Text("Cantidad: ${mapNum(v, "cantidad")}", color = scheme.onSurface)
                    Text("Activos: ${mapNum(v, "activos")}", color = scheme.onSurfaceVariant)
                    Text("Inactivos: ${mapNum(v, "inactivos")}", color = scheme.onSurfaceVariant)
                }
                2 -> TabPlanUsuario(
                    detalle = detalle,
                    suscripcionesRepository = suscripcionesRepository,
                    onRefresh = onRefresh
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onHistorial,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
            ) {
                Icon(Icons.Outlined.History, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ver historial de estados")
            }
            if (!detalle.anonima) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onGestionar,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                ) { Text("Gestionar estado de cuenta") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onVolver,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Volver a la lista") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun mapNum(map: Map<String, Any?>?, key: String): String {
    val v = map?.get(key) ?: return "0"
    return when (v) {
        is Number -> v.toInt().toString()
        else -> v.toString()
    }
}

private fun mapStr(map: Map<String, Any?>?, key: String): String? =
    map?.get(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }

private fun mapBool(map: Map<String, Any?>?, key: String): Boolean {
    val v = map?.get(key) ?: return false
    return when (v) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        else -> v.toString().equals("true", ignoreCase = true)
    }
}

@Composable
private fun TabPlanUsuario(
    detalle: AdminUsuarioDetalleDto,
    suscripcionesRepository: AdminSuscripcionesRepository?,
    onRefresh: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var motivoReject by remember { mutableStateOf("") }
    var motivoCancel by remember { mutableStateOf("") }
    var confirmReject by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var observacion by remember { mutableStateOf("") }

    val s = detalle.suscripcion
    val sol = detalle.solicitudPremiumAbierta

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = scheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WorkspacePremium, null, tint = scheme.secondary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Plan actual", fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                }
                if (s == null) {
                    Text("Sin suscripción registrada.", color = scheme.onSurfaceVariant)
                } else {
                    InfoLine("Plan", mapStr(s, "plan") ?: mapStr(s, "nombrePlan"))
                    InfoLine("Estado", mapStr(s, "estado"))
                    InfoLine("Modalidad", mapStr(s, "modalidad") ?: "—")
                    InfoLine("Origen", mapStr(s, "origen"))
                    InfoLine("Inicio", FechaMyCar.fechaHora(mapStr(s, "inicioUtc")))
                    InfoLine(
                        "Fin",
                        mapStr(s, "finUtc")?.let { FechaMyCar.fechaHora(it) } ?: "Sin vencimiento"
                    )
                    when {
                        mapBool(s, "esPruebaGratuita") ->
                            Text("Periodo de prueba — admin puede cancelar.", color = Orange, fontSize = 13.sp)
                        mapBool(s, "esSandbox") ->
                            Text("Premium sandbox — admin puede cancelar.", color = scheme.secondary, fontSize = 13.sp)
                    }
                }
            }
        }

        if (sol != null) {
            Surface(
                color = Orange.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Solicitud Premium abierta", fontWeight = FontWeight.SemiBold, color = Orange)
                    InfoLine("Código", mapStr(sol, "codigo"))
                    InfoLine("Modalidad", mapStr(sol, "modalidad"))
                    InfoLine("Estado solicitud", mapStr(sol, "estado"))
                    InfoLine("Creada", FechaMyCar.fechaHora(mapStr(sol, "creadoUtc")))
                    mapStr(sol, "referenciaOperacion")?.let {
                        InfoLine("Operación", "$it (${mapStr(sol, "estadoOperacion") ?: "—"})")
                    }
                    err?.let { Text(it, color = Danger) }
                    msg?.let { Text(it, color = Green) }

                    val estadoSol = mapStr(sol, "estado")
                    val puedeAprobar = mapBool(sol, "puedeAprobar")
                    if (suscripcionesRepository != null && estadoSol == "PENDIENTE_APROBACION") {
                        if (!puedeAprobar) {
                            Text(
                                "Aún no se puede aprobar (pago no confirmado o configuración).",
                                color = Danger,
                                fontSize = 13.sp
                            )
                        }
                        OutlinedTextField(
                            value = observacion,
                            onValueChange = { observacion = it },
                            label = { Text("Observación (opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val codigo = mapStr(sol, "codigo") ?: return@Button
                                scope.launch {
                                    busy = true; err = null; msg = null
                                    try {
                                        suscripcionesRepository.aprobar(codigo, observacion.ifBlank { null })
                                        msg = "Solicitud aprobada. Premium activado."
                                        onRefresh()
                                    } catch (ex: Exception) {
                                        err = ex.message
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy && puedeAprobar,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) { Text("Aprobar solicitud") }
                    } else if (estadoSol == "PENDIENTE_PAGO") {
                        Text(
                            "Solicitud antigua (esperaba pago). Recházala y pide de nuevo con «Pagar Premium».",
                            color = Orange,
                            fontSize = 13.sp
                        )
                    }

                    if (suscripcionesRepository != null &&
                        (estadoSol == "PENDIENTE_APROBACION" || estadoSol == "PENDIENTE_PAGO")
                    ) {
                        if (confirmReject) {
                            OutlinedTextField(
                                value = motivoReject,
                                onValueChange = { motivoReject = it },
                                label = { Text("Motivo del rechazo (obligatorio)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { confirmReject = false }, enabled = !busy) {
                                    Text("Cancelar")
                                }
                                Button(
                                    onClick = {
                                        val codigo = mapStr(sol, "codigo") ?: return@Button
                                        if (motivoReject.isBlank()) {
                                            err = "Indica el motivo del rechazo."
                                            return@Button
                                        }
                                        scope.launch {
                                            busy = true; err = null; msg = null
                                            try {
                                                suscripcionesRepository.rechazar(codigo, motivoReject.trim())
                                                msg = "Solicitud rechazada."
                                                confirmReject = false
                                                onRefresh()
                                            } catch (ex: Exception) {
                                                err = ex.message
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    },
                                    enabled = !busy,
                                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                                ) { Text("Confirmar rechazo") }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { confirmReject = true; err = null },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Rechazar solicitud") }
                        }
                    }
                }
            }
        } else {
            Text("Sin solicitud Premium pendiente.", color = scheme.onSurfaceVariant)
        }

        val puedeCancelar = mapBool(s, "puedeCancelarAdmin")
        if (suscripcionesRepository != null && puedeCancelar) {
            Surface(
                color = Danger.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Cancel, null, tint = Danger, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cancelar Premium (prueba/sandbox)", fontWeight = FontWeight.SemiBold, color = Danger)
                    }
                    Text("El usuario vuelve a FREE y se concilian vehículos activos.", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                    if (confirmCancel) {
                        OutlinedTextField(
                            value = motivoCancel,
                            onValueChange = { motivoCancel = it },
                            label = { Text("Motivo (obligatorio)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { confirmCancel = false }, enabled = !busy) {
                                Text("Volver")
                            }
                            Button(
                                onClick = {
                                    if (motivoCancel.isBlank()) {
                                        err = "Indica el motivo de cancelación."
                                        return@Button
                                    }
                                    scope.launch {
                                        busy = true; err = null; msg = null
                                        try {
                                            suscripcionesRepository.cancelarPremium(
                                                detalle.idUsuario,
                                                motivoCancel.trim()
                                            )
                                            msg = "Premium cancelado. Usuario en FREE."
                                            confirmCancel = false
                                            onRefresh()
                                        } catch (ex: Exception) {
                                            err = ex.message
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                enabled = !busy,
                                colors = ButtonDefaults.buttonColors(containerColor = Danger)
                            ) { Text("Confirmar cancelación") }
                        }
                    } else {
                        Button(
                            onClick = { confirmCancel = true; err = null },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Danger)
                        ) { Text("Anular Premium / detener experiencia") }
                    }
                }
            }
        }

        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Teal)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    val scheme = MaterialTheme.colorScheme
    if (value.isNullOrBlank()) return
    Text("$label: $value", color = scheme.onSurface, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
}

@Composable
private fun UsuariosGestionScreen(
    detalle: AdminUsuarioDetalleDto,
    onVolver: () -> Unit,
    onSuspender: () -> Unit,
    onBloquear: () -> Unit,
    onDesbloquear: () -> Unit,
    onReactivar: () -> Unit,
    onAsistencia: () -> Unit,
    onHistorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val estado = detalle.estado.orEmpty()
    Scaffold(
        modifier = modifier,
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Gestionar estado") },
                navigationIcon = { TextButton(onClick = onVolver) { Text("←") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Estado actual: ${estado.replace('_', ' ')}", fontWeight = FontWeight.Bold, color = scheme.onSurface)
            when (estado) {
                "ACTIVA" -> {
                    AccionBtn("Suspender cuenta", Danger, onSuspender, Icons.Outlined.PauseCircle)
                    AccionBtn("Bloquear cuenta", Danger, onBloquear, Icons.Outlined.Lock)
                    Text(
                        "Gestionar eliminación: el titular usa Perfil → Eliminar cuenta (plazo 30 días).",
                        color = scheme.onSurfaceVariant
                    )
                }
                "SUSPENDIDA" -> {
                    AccionBtn("Levantar suspensión", Teal, onReactivar, Icons.Outlined.LockOpen)
                    AccionBtn("Ver historial", scheme.primary, onHistorial, Icons.Outlined.History)
                }
                "BLOQUEADA" -> {
                    AccionBtn("Desbloquear", Teal, onDesbloquear, Icons.Outlined.LockOpen)
                    AccionBtn("Ver historial", scheme.primary, onHistorial, Icons.Outlined.History)
                }
                "PENDIENTE_ELIMINACION" -> {
                    AccionBtn("Reactivar cuenta", Teal, onAsistencia, Icons.Outlined.VerifiedUser)
                    AccionBtn("Ver historial", scheme.primary, onHistorial, Icons.Outlined.History)
                }
                "ELIMINADA" -> Text("Sin acciones de reactivación. Solo consulta de constancia.", color = scheme.onSurfaceVariant)
            }
            val recup = detalle.restriccionRecuperacion
            val bloqueado = when (val b = recup?.get("bloqueado")) {
                is Boolean -> b
                else -> b?.toString().equals("true", ignoreCase = true)
            }
            if (estado == "ACTIVA" && bloqueado) {
                Text("Bloqueo de recuperación activo (no impide login).", color = scheme.onSurfaceVariant)
                AccionBtn("Ver historial", scheme.primary, onHistorial)
            }
            Spacer(Modifier.weight(1f, fill = true))
            OutlinedButton(onClick = onVolver, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Volver")
            }
        }
    }
}

@Composable
private fun AccionBtn(
    text: String,
    color: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FormMotivoScreen(
    titulo: String,
    accion: String,
    peligro: Boolean,
    onCancel: () -> Unit,
    onConfirm: suspend (motivo: String, comentario: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var motivo by remember { mutableStateOf("") }
    var comentario by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(
        modifier = modifier,
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = { TextButton(onClick = onCancel, enabled = !busy) { Text("←") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = motivo, onValueChange = { motivo = it },
                label = { Text("Motivo obligatorio") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = comentario, onValueChange = { comentario = it },
                label = { Text("Comentario opcional") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            Text("Esta acción se audita y puede revocar sesiones.", color = scheme.onSurfaceVariant)
            error?.let { Text(it, color = Danger) }
            Spacer(Modifier.weight(1f, fill = true))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Cancelar")
                }
                Button(
                    onClick = { confirmar = true },
                    enabled = !busy && motivo.trim().length >= 3,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (peligro) Danger else Teal)
                ) { Text(accion) }
            }
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmar = false },
            title = { Text("Confirmar") },
            text = { Text("¿Confirmas «$accion»?") },
            dismissButton = { TextButton(onClick = { confirmar = false }, enabled = !busy) { Text("Cancelar") } },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            try {
                                onConfirm(motivo.trim(), comentario.ifBlank { null })
                                confirmar = false
                            } catch (ex: Exception) {
                                error = ex.message; confirmar = false
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy
                ) { Text("Confirmar") }
            }
        )
    }
}

@Composable
private fun FormBloquearScreen(
    onCancel: () -> Unit,
    onConfirm: suspend (motivo: String, minutos: Int, observacion: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var motivo by remember { mutableStateOf("") }
    var minutos by remember { mutableStateOf("60") }
    var obs by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Scaffold(
        modifier = modifier,
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Bloquear cuenta") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("←") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tipo: bloqueo administrativo de acceso.", color = scheme.onSurfaceVariant)
            OutlinedTextField(
                value = motivo, onValueChange = { motivo = it }, label = { Text("Motivo obligatorio") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = minutos, onValueChange = { minutos = it.filter { c -> c.isDigit() } },
                label = { Text("Duración (minutos)") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = obs, onValueChange = { obs = it }, label = { Text("Observación opcional") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            error?.let { Text(it, color = Danger) }
            Spacer(Modifier.weight(1f, fill = true))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp)) { Text("Cancelar") }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            try {
                                onConfirm(motivo.trim(), minutos.toIntOrNull() ?: 0, obs.ifBlank { null })
                            } catch (ex: Exception) {
                                error = ex.message
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && motivo.trim().length >= 3,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("Bloquear cuenta") }
            }
        }
    }
}

@Composable
private fun UsuariosHistorialScreen(
    repository: AdminUsuariosRepository,
    id: Long,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var items by remember { mutableStateOf<List<com.mycar.app.features.admin.data.AdminHistorialItemDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        try {
            items = repository.historial(id)
        } catch (ex: Exception) {
            error = ex.message
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Historial de estados") },
                navigationIcon = { TextButton(onClick = onVolver) { Text("←") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            error?.let { item { Text(it, color = Danger) } }
            items(items) { h ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = scheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(h.accion.orEmpty(), fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Text("${h.estadoAnterior} → ${h.estadoPosterior}", color = scheme.onSurfaceVariant)
                        Text(h.motivo.orEmpty(), color = scheme.onSurfaceVariant)
                        Text(
                            FechaMyCar.fechaHora(h.creadoUtc),
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactivarAsistidaScreen(
    repository: AdminUsuariosRepository,
    id: Long,
    detalle: AdminUsuarioDetalleDto?,
    onVolver: () -> Unit,
    onHecho: () -> Unit,
    onConsultarCodigo: (() -> Unit)? = null,
    onAbrirPruebas: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var data by remember { mutableStateOf<Map<String, Any>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var motivo by remember { mutableStateOf("") }
    var observacion by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var otpLocal by remember { mutableStateOf<String?>(null) }
    var mensajeOk by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    var confirmar by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val correoDestino = remember(detalle, data) {
        (data?.get("correo") ?: detalle?.correo)?.toString()?.trim().orEmpty()
    }

    suspend fun cargarOtpLocal() {
        if (!BuildConfig.LOCAL_TEST_TOOLS || correoDestino.isBlank()) {
            otpLocal = null
            return
        }
        otpLocal = consultarOtpSimulado(correoDestino, "VERIF_REACTIV_ADMIN")
    }

    fun refrescar(alsoOtp: Boolean = false) {
        scope.launch {
            try {
                data = repository.estadoReactivacionAsistida(id)
                error = null
                if (alsoOtp || data?.get("fase")?.toString() == "PENDIENTE_VERIFICACION") {
                    cargarOtpLocal()
                } else if (data?.get("fase")?.toString() != "PENDIENTE_VERIFICACION") {
                    otpLocal = null
                }
            } catch (ex: Exception) {
                error = ex.message
            } finally {
                cargando = false
            }
        }
    }

    LaunchedEffect(id) {
        cargando = true
        refrescar(alsoOtp = true)
    }

    val fase = data?.get("fase")?.toString() ?: "NO_INICIADA"
    val titularOk = data?.get("titularVerificado") == true || data?.get("titularVerificado")?.toString() == "true"
    val puede = data?.get("puedeReactivar") == true || data?.get("puedeReactivar")?.toString() == "true"
    val sol = data?.get("solicitudEliminacion") as? Map<*, *>
    val ver = data?.get("verificacion") as? Map<*, *>

    Scaffold(
        modifier = modifier,
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = { Text("Reactivar cuenta") },
                navigationIcon = { TextButton(onClick = onVolver, enabled = !busy) { Text("←") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                detalle?.nombreCompleto
                    ?: listOfNotNull(detalle?.nombre, detalle?.apellido).joinToString(" ").ifBlank { "Usuario #$id" },
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
            Text("Estado actual: ${(data?.get("estado") ?: detalle?.estado).toString().replace('_', ' ')}", color = scheme.onSurfaceVariant)
            InfoLine("Solicitud de eliminación", FechaMyCar.fechaHora(sol?.get("solicitadaUtc")?.toString()))
            InfoLine("Fecha límite", FechaMyCar.fecha(sol?.get("venceUtc")?.toString()))
            InfoLine("Días restantes", data?.get("diasRestantes")?.toString())
            error?.let { Text(it, color = Danger) }
            mensajeOk?.let { Text(it, color = Green, fontWeight = FontWeight.SemiBold) }

            when {
                cargando && data == null -> {
                    CircularProgressIndicator(Modifier.padding(24.dp), color = Teal)
                }
                fase == "REACTIVADA" -> {
                    Text(
                        "Esta cuenta ya no está pendiente de eliminación. Estado actual: ${(data?.get("estado") ?: detalle?.estado)}",
                        color = scheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = onVolver,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Volver al detalle") }
                }
                fase == "NO_INICIADA" -> {
                    Text(
                        "Para reactivar se requiere verificación del titular. No se reenvía código al abrir esta pantalla.",
                        color = scheme.onSurfaceVariant
                    )
                    AccionBtn(
                        "Iniciar verificación del titular",
                        Teal,
                        onClick = {
                            scope.launch {
                                busy = true; error = null; mensajeOk = null
                                try {
                                    repository.iniciarVerificacionAsistida(id)
                                    mensajeOk = "Verificación simulada enviada correctamente"
                                    codigo = ""
                                    refrescar(alsoOtp = true)
                                } catch (ex: Exception) {
                                    error = ex.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        Icons.Outlined.VerifiedUser
                    )
                }
                fase == "EXPIRADA" -> {
                    Text("Código vencido", color = Danger, fontWeight = FontWeight.Bold)
                    Text("La verificación anterior expiró. Puedes reenviar un código nuevo.", color = scheme.onSurfaceVariant)
                    AccionBtn(
                        "Reenviar código",
                        Teal,
                        onClick = {
                            scope.launch {
                                busy = true; error = null; mensajeOk = null
                                try {
                                    repository.iniciarVerificacionAsistida(id)
                                    mensajeOk = "Código reenviado"
                                    codigo = ""
                                    refrescar(alsoOtp = true)
                                } catch (ex: Exception) {
                                    error = ex.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        Icons.Outlined.VerifiedUser
                    )
                }
                fase == "PENDIENTE_VERIFICACION" -> {
                    Text("Introducir código", fontWeight = FontWeight.Bold, color = scheme.onSurface)
                    Text(
                        "Hay una verificación vigente. No se genera otro correo al reabrir esta pantalla.",
                        color = scheme.onSurfaceVariant
                    )
                    InfoLine("Vence", FechaMyCar.fechaHora(ver?.get("expiraUtc")?.toString()))
                    CodigoPruebaLocalBanner(codigo = otpLocal)
                    OutlinedTextField(
                        value = codigo,
                        onValueChange = { if (it.length <= 6) codigo = it.filter { c -> c.isDigit() } },
                        label = { Text("Código de verificación") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !busy,
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true; error = null; mensajeOk = null
                                try {
                                    val r = repository.verificarCodigoAsistida(id, codigo)
                                    mensajeOk = r["mensaje"]?.toString() ?: "Identidad del titular verificada"
                                    codigo = ""
                                    otpLocal = null
                                    refrescar()
                                } catch (ex: Exception) {
                                    error = ex.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && codigo.length == 6,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal)
                    ) { Text("Verificar") }
                    if (onConsultarCodigo != null) {
                        OutlinedButton(
                            onClick = {
                                scope.launch { cargarOtpLocal() }
                                onConsultarCodigo()
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("Consultar código en bandeja de pruebas") }
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                busy = true; error = null
                                try {
                                    val r = repository.iniciarVerificacionAsistida(id)
                                    if (r["reiniciada"] == true || r["reiniciada"]?.toString() == "true") {
                                        mensajeOk = "Código reenviado"
                                        codigo = ""
                                    } else {
                                        mensajeOk = "La verificación vigente se conservó (sin nuevo correo)"
                                    }
                                    refrescar(alsoOtp = true)
                                } catch (ex: Exception) {
                                    error = ex.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy
                    ) { Text("Reenviar código") }
                }
                fase == "TITULAR_VERIFICADO" || titularOk -> {
                    Text("Identidad del titular verificada", color = Green, fontWeight = FontWeight.Bold)
                    Text("Introduce el motivo obligatorio y confirma la reactivación.", color = scheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = motivo,
                        onValueChange = { motivo = it },
                        label = { Text("Motivo administrativo obligatorio") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !busy
                    )
                    OutlinedTextField(
                        value = observacion,
                        onValueChange = { observacion = it },
                        label = { Text("Observación opcional") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !busy
                    )
                    Button(
                        onClick = { confirmar = true },
                        enabled = !busy && puede && motivo.trim().length >= 3,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green)
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Confirmar reactivación")
                    }
                }
            }

            if (fase != "REACTIVADA") {
                OutlinedButton(
                    onClick = onVolver,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Cancelar") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmar = false },
            title = { Text("¿Deseas reactivar esta cuenta?") },
            text = {
                Text("Se cancelará la solicitud de eliminación vigente. El titular deberá iniciar una sesión nueva.")
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }, enabled = !busy) { Text("Cancelar") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            try {
                                repository.confirmarReactivacionAsistida(id, motivo.trim(), observacion.ifBlank { null })
                                confirmar = false
                                onHecho()
                            } catch (ex: Exception) {
                                error = ex.message
                                confirmar = false
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy
                ) { Text("Confirmar reactivación") }
            }
        )
    }
}
