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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycar.app.features.admin.data.AdminCatalogosRepository
import com.mycar.app.features.admin.data.CatalogoItemDto
import com.mycar.app.features.admin.data.TallerGlobalBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Teal = Color(0xFF007E98)
private val Danger = Color(0xFFC53030)
private val Green = Color(0xFF2F855A)

private enum class CatalogosDestino { HOME, TIPOS, ASEGURADORAS, TALLERES }

@Composable
fun CatalogosAdminRoute(
    repository: AdminCatalogosRepository,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var destino by remember { mutableStateOf(CatalogosDestino.HOME) }
    BackHandler {
        if (destino == CatalogosDestino.HOME) onVolver() else destino = CatalogosDestino.HOME
    }
    when (destino) {
        CatalogosDestino.HOME -> CatalogosHomeScreen(
            onVolver = onVolver,
            onTipos = { destino = CatalogosDestino.TIPOS },
            onAseg = { destino = CatalogosDestino.ASEGURADORAS },
            onTalleres = { destino = CatalogosDestino.TALLERES },
            modifier = modifier
        )
        CatalogosDestino.TIPOS -> CatalogoNombreCrudScreen(
            titulo = "Tipos de mantenimiento",
            itemIcon = Icons.Outlined.Build,
            accent = scheme.onSurface,
            listar = { q, e -> repository.listarTipos(q, e) },
            crear = { repository.crearTipo(it) },
            editar = { id, n -> repository.editarTipo(id, n) },
            desactivar = { repository.desactivarTipo(it) },
            reactivar = { repository.reactivarTipo(it) },
            bloqueado = { it.codigo.equals("OTRO", true) },
            onVolver = { destino = CatalogosDestino.HOME },
            modifier = modifier
        )
        CatalogosDestino.ASEGURADORAS -> CatalogoNombreCrudScreen(
            titulo = "Aseguradoras SOAT",
            itemIcon = Icons.Outlined.VerifiedUser,
            accent = scheme.secondary,
            listar = { q, e -> repository.listarAseguradoras(q, e) },
            crear = { repository.crearAseguradora(it) },
            editar = { id, n -> repository.editarAseguradora(id, n) },
            desactivar = { repository.desactivarAseguradora(it) },
            reactivar = { repository.reactivarAseguradora(it) },
            bloqueado = { it.permiteNombreLibre == true || it.codigo.equals("OTRA", true) },
            onVolver = { destino = CatalogosDestino.HOME },
            modifier = modifier
        )
        CatalogosDestino.TALLERES -> TalleresGlobalesAdminScreen(
            repository = repository,
            onVolver = { destino = CatalogosDestino.HOME },
            modifier = modifier
        )
    }
}

@Composable
private fun CatalogosHomeScreen(
    onVolver: () -> Unit,
    onTipos: () -> Unit,
    onAseg: () -> Unit,
    onTalleres: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VolverBtn(onVolver)
            Text("Catálogos", fontWeight = FontWeight.Bold, color = scheme.onSurface, style = MaterialTheme.typography.titleLarge)
            Text("Administra tipos, aseguradoras SOAT y talleres asociados.", color = scheme.onSurfaceVariant)
            CatalogoCard(
                titulo = "Tipos de mantenimiento",
                sub = "RF17 / RF22 — catálogo activo",
                icon = Icons.Outlined.Build,
                accent = scheme.onSurface,
                onClick = onTipos
            )
            CatalogoCard(
                titulo = "Aseguradoras SOAT",
                sub = "Selector de registro y corrección",
                icon = Icons.Outlined.Shield,
                accent = scheme.secondary,
                onClick = onAseg
            )
            CatalogoCard(
                titulo = "Talleres asociados",
                sub = "Catálogo global MyCar",
                icon = Icons.Outlined.Store,
                accent = scheme.onSurface,
                onClick = onTalleres
            )
        }
    }
}

@Composable
private fun CatalogoCard(
    titulo: String,
    sub: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface)
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon, accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(titulo, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                Text(sub, color = scheme.onSurfaceVariant)
                Text("Disponible", color = scheme.secondary, fontWeight = FontWeight.Medium)
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun CatalogoNombreCrudScreen(
    titulo: String,
    itemIcon: ImageVector,
    accent: Color,
    listar: suspend (String?, String) -> com.mycar.app.features.admin.data.CatalogoListaDto,
    crear: suspend (String) -> CatalogoItemDto,
    editar: suspend (Long, String) -> CatalogoItemDto,
    desactivar: suspend (Long) -> CatalogoItemDto,
    reactivar: suspend (Long) -> CatalogoItemDto,
    bloqueado: (CatalogoItemDto) -> Boolean,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var qDebounced by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf("TODOS") }
    var items by remember { mutableStateOf<List<CatalogoItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var formItem by remember { mutableStateOf<CatalogoItemDto?>(null) }
    var creando by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<CatalogoItemDto, String>?>(null) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(q) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(350)
            qDebounced = q
        }
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                items = listar(qDebounced.ifBlank { null }, estado).items
            } catch (ex: Exception) {
                error = ex.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(qDebounced, estado) { refresh() }

    if (creando || formItem != null) {
        val editando = formItem
        NombreFormScreen(
            titulo = if (editando == null) "Nuevo" else "Editar",
            fieldIcon = itemIcon,
            inicial = editando?.nombre.orEmpty(),
            onCancel = { creando = false; formItem = null },
            onSave = { nombre ->
                scope.launch {
                    try {
                        if (editando == null) crear(nombre) else editar(editando.id, nombre)
                        creando = false
                        formItem = null
                        refresh()
                    } catch (ex: Exception) {
                        error = ex.message
                    }
                }
            }
        )
        return
    }

    Scaffold(
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        modifier = modifier
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            VolverBtn(onVolver)
            Text(titulo, fontWeight = FontWeight.Bold, color = scheme.onSurface, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar…") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                },
                trailingIcon = {
                    if (q.isNotEmpty()) {
                        IconButton(onClick = { q = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Limpiar", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TODOS", "ACTIVOS", "INACTIVOS").forEach { chip ->
                    FilterChip(
                        selected = estado == chip,
                        onClick = { estado = chip },
                        label = { Text(chip.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { creando = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Agregar")
            }
            Spacer(Modifier.height(8.dp))
            error?.let { Text(it, color = Danger) }
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Teal)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { item ->
                        val activo = item.activo == true
                        CatalogoListItem(
                            nombre = item.nombre.orEmpty(),
                            detalle = item.codigo.orEmpty(),
                            activo = activo,
                            icon = itemIcon,
                            accent = accent,
                            bloqueado = bloqueado(item),
                            onEditar = { formItem = item },
                            onDesactivar = { confirm = item to "DESACTIVAR" },
                            onReactivar = { confirm = item to "REACTIVAR" }
                        )
                    }
                }
            }
        }
    }

    confirm?.let { (item, accion) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (accion == "DESACTIVAR") "Desactivar" else "Reactivar") },
            text = { Text("¿Confirmas ${accion.lowercase()} «${item.nombre}»?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            if (accion == "DESACTIVAR") desactivar(item.id) else reactivar(item.id)
                            confirm = null
                            refresh()
                        } catch (ex: Exception) {
                            error = ex.message
                            confirm = null
                        }
                    }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun CatalogoListItem(
    nombre: String,
    detalle: String?,
    activo: Boolean,
    icon: ImageVector,
    accent: Color,
    bloqueado: Boolean,
    onEditar: () -> Unit,
    onDesactivar: () -> Unit,
    onReactivar: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, if (activo) accent else scheme.onSurfaceVariant, size = 36.dp, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(nombre, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    if (!detalle.isNullOrBlank()) {
                        Text(detalle, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        if (activo) "Activo" else "Inactivo",
                        color = if (activo) Green else scheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (!bloqueado) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onEditar) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = scheme.onSurface)
                        Spacer(Modifier.width(4.dp))
                        Text("Editar", color = scheme.onSurface)
                    }
                    if (activo) {
                        TextButton(onClick = onDesactivar) {
                            Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(18.dp), tint = Danger)
                            Spacer(Modifier.width(4.dp))
                            Text("Desactivar", color = Danger)
                        }
                    } else {
                        TextButton(onClick = onReactivar) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = scheme.secondary)
                            Spacer(Modifier.width(4.dp))
                            Text("Reactivar", color = scheme.secondary)
                        }
                    }
                }
            } else {
                Text("Protegido", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NombreFormScreen(
    titulo: String,
    fieldIcon: ImageVector,
    inicial: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var nombre by remember { mutableStateOf(inicial) }
    Scaffold(
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onCancel) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cancelar", color = scheme.onSurface)
            }
            Text(titulo, fontWeight = FontWeight.Bold, color = scheme.onSurface, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre") },
                singleLine = true,
                leadingIcon = {
                    Icon(fieldIcon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            )
            Button(
                onClick = { onSave(nombre.trim()) },
                enabled = nombre.trim().length >= 2,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun TalleresGlobalesAdminScreen(
    repository: AdminCatalogosRepository,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var qDebounced by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf("TODOS") }
    var items by remember { mutableStateOf<List<CatalogoItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<CatalogoItemDto?>(null) }
    var creando by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<CatalogoItemDto, String>?>(null) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(q) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(350)
            qDebounced = q
        }
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                items = repository.listarTalleres(qDebounced.ifBlank { null }, estado).items
            } catch (ex: Exception) {
                error = ex.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(qDebounced, estado) { refresh() }

    if (creando || form != null) {
        TallerFormScreen(
            inicial = form,
            onCancel = { creando = false; form = null },
            onSave = { body ->
                scope.launch {
                    try {
                        val edit = form
                        if (edit == null) repository.crearTaller(body) else repository.editarTaller(edit.id, body)
                        creando = false
                        form = null
                        refresh()
                    } catch (ex: Exception) {
                        error = ex.message
                    }
                }
            }
        )
        return
    }

    Scaffold(
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        modifier = modifier
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            VolverBtn(onVolver)
            Text("Talleres asociados", fontWeight = FontWeight.Bold, color = scheme.onSurface, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar…") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                },
                trailingIcon = {
                    if (q.isNotEmpty()) {
                        IconButton(onClick = { q = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Limpiar", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TODOS", "ACTIVOS", "INACTIVOS").forEach { chip ->
                    FilterChip(
                        selected = estado == chip,
                        onClick = { estado = chip },
                        label = { Text(chip.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { creando = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Agregar taller")
            }
            Spacer(Modifier.height(8.dp))
            error?.let { Text(it, color = Danger) }
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Teal)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { item ->
                        val activo = item.estado.equals("ACTIVO", true)
                        CatalogoListItem(
                            nombre = item.nombre.orEmpty(),
                            detalle = item.telefono,
                            activo = activo,
                            icon = Icons.Outlined.Store,
                            accent = scheme.onSurface,
                            bloqueado = false,
                            onEditar = { form = item },
                            onDesactivar = { confirm = item to "DESACTIVAR" },
                            onReactivar = { confirm = item to "REACTIVAR" }
                        )
                    }
                }
            }
        }
    }

    confirm?.let { (item, accion) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (accion == "DESACTIVAR") "Desactivar taller" else "Reactivar taller") },
            text = {
                Text(
                    if (accion == "DESACTIVAR")
                        "Dejará de aparecer en el catálogo de usuarios. Los vínculos existentes se conservan."
                    else "Volverá a aparecer en el catálogo global activo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            if (accion == "DESACTIVAR") repository.desactivarTaller(item.id)
                            else repository.reactivarTaller(item.id)
                            confirm = null
                            refresh()
                        } catch (ex: Exception) {
                            error = ex.message
                            confirm = null
                        }
                    }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TallerFormScreen(
    inicial: CatalogoItemDto?,
    onCancel: () -> Unit,
    onSave: (TallerGlobalBody) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var nombre by remember { mutableStateOf(inicial?.nombre.orEmpty()) }
    var telefono by remember { mutableStateOf(inicial?.telefono.orEmpty()) }
    var correo by remember { mutableStateOf(inicial?.correo.orEmpty()) }
    var direccion by remember { mutableStateOf(inicial?.direccion.orEmpty()) }
    var notas by remember { mutableStateOf(inicial?.notasPublicas.orEmpty()) }
    var wa by remember { mutableStateOf(inicial?.tieneWhatsapp == true) }
    var auth by remember { mutableStateOf(inicial?.contactoAutorizado == true) }

    Scaffold(
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onCancel) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cancelar", color = scheme.onSurface)
            }
            Text(
                if (inicial == null) "Nuevo taller" else "Editar taller",
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                nombre, { nombre = it }, Modifier.fillMaxWidth(),
                label = { Text("Nombre *") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Store, null, Modifier.size(22.dp), scheme.onSurfaceVariant) }
            )
            OutlinedTextField(
                telefono, { telefono = it }, Modifier.fillMaxWidth(),
                label = { Text("Teléfono (9 dígitos)") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Phone, null, Modifier.size(22.dp), scheme.onSurfaceVariant) }
            )
            OutlinedTextField(
                correo, { correo = it }, Modifier.fillMaxWidth(),
                label = { Text("Correo") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Email, null, Modifier.size(22.dp), scheme.onSurfaceVariant) }
            )
            OutlinedTextField(
                direccion, { direccion = it }, Modifier.fillMaxWidth(),
                label = { Text("Dirección") },
                leadingIcon = { Icon(Icons.Outlined.LocationOn, null, Modifier.size(22.dp), scheme.onSurfaceVariant) }
            )
            OutlinedTextField(notas, { notas = it }, Modifier.fillMaxWidth(), label = { Text("Notas públicas") })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tiene WhatsApp", color = scheme.onSurface)
                Switch(checked = wa, onCheckedChange = { wa = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Contacto autorizado", color = scheme.onSurface)
                Switch(checked = auth, onCheckedChange = { auth = it })
            }
            Button(
                onClick = {
                    onSave(
                        TallerGlobalBody(
                            nombre = nombre.trim(),
                            telefono = telefono.trim().ifBlank { null },
                            correo = correo.trim().ifBlank { null },
                            direccion = direccion.trim().ifBlank { null },
                            tieneWhatsapp = wa,
                            contactoAutorizado = auth,
                            notasPublicas = notas.trim().ifBlank { null }
                        )
                    )
                },
                enabled = nombre.trim().length >= 2,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Guardar")
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun VolverBtn(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    TextButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text("Volver", color = scheme.onSurface)
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    tint: Color,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    Box(
        Modifier.size(size).background(tint.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}
