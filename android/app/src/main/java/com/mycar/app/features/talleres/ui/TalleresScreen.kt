package com.mycar.app.features.talleres.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.talleres.domain.DatosTallerFormulario
import com.mycar.app.features.talleres.domain.FiltroOrigenTaller
import com.mycar.app.features.talleres.domain.Taller
import com.mycar.app.features.talleres.domain.TallerGlobal
import com.mycar.app.features.talleres.domain.TalleresRepository
import com.mycar.app.features.talleres.domain.ValidadorFormularioTaller
import com.mycar.app.features.talleres.viewmodel.EstadoTalleres
import com.mycar.app.features.talleres.viewmodel.FaseTalleres
import com.mycar.app.features.talleres.viewmodel.TalleresViewModel

private val Fondo = Color(0xFFF0F5FA)
private val Azul = Color(0xFF12285F)
private val Celeste = Color(0xFF007E98)
private val VerdeAsociado = Color(0xFF1B7A4E)
private val VerdeAsociadoBg = Color(0xFFE6F6EE)
private val AzulManualBg = Color(0xFFE8EEF8)
private val Estrella = Color(0xFFE6A817)

@Composable
fun TalleresRoute(
    repository: TalleresRepository,
    onVolver: (() -> Unit)? = null,
    embebidoEnHistorial: Boolean = false,
    onFormularioAbierto: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val factory = remember(repository) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TalleresViewModel(repository) as T
        }
    }
    val vm: TalleresViewModel = viewModel(key = "rf34.talleres", factory = factory)
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.fase) {
        onFormularioAbierto(
            estado.fase == FaseTalleres.FORMULARIO ||
                estado.fase == FaseTalleres.CATALOGO_ASOCIADO ||
                estado.fase == FaseTalleres.DETALLE
        )
    }
    when (estado.fase) {
        FaseTalleres.FORMULARIO -> FormularioTallerScreen(
            editando = estado.editando,
            ocupado = estado.ocupado,
            mensaje = estado.mensaje,
            onCancelar = vm::cancelarFormulario,
            onGuardar = vm::guardar,
            onIrAsociado = { vm.abrirCatalogoAsociado() },
            modifier = modifier
        )
        FaseTalleres.CATALOGO_ASOCIADO -> CatalogoAsociadoScreen(
            catalogo = estado.catalogo,
            busqueda = estado.catalogoBusqueda,
            ocupado = estado.ocupado,
            mensaje = estado.mensaje,
            onVolver = vm::cancelarFormulario,
            onBuscar = vm::buscarEnCatalogo,
            onVincular = vm::vincularAsociado,
            modifier = modifier
        )
        FaseTalleres.DETALLE -> estado.detalle?.let { d ->
            DetalleTallerScreen(
                taller = d,
                ocupado = estado.ocupado,
                mensaje = estado.mensaje,
                onVolver = vm::cancelarFormulario,
                onEditar = { vm.abrirEdicion(d) },
                onFavorito = { vm.alternarFavorito(d) },
                onRetirar = { vm.retirar(d) },
                modifier = modifier
            )
        } ?: TalleresListScreen(
            estado = estado, onVolver = onVolver, embebidoEnHistorial = embebidoEnHistorial,
            onAgregar = vm::abrirAlta, onBuscarAsociado = { vm.abrirCatalogoAsociado() },
            onFiltro = vm::cambiarFiltro, onBusqueda = vm::cambiarBusqueda,
            onAbrirDetalle = vm::abrirDetalle, onFavorito = vm::alternarFavorito,
            onReintentar = vm::cargar, modifier = modifier
        )
        else -> TalleresListScreen(
            estado = estado,
            onVolver = onVolver,
            embebidoEnHistorial = embebidoEnHistorial,
            onAgregar = vm::abrirAlta,
            onBuscarAsociado = { vm.abrirCatalogoAsociado() },
            onFiltro = vm::cambiarFiltro,
            onBusqueda = vm::cambiarBusqueda,
            onAbrirDetalle = vm::abrirDetalle,
            onFavorito = vm::alternarFavorito,
            onReintentar = vm::cargar,
            modifier = modifier
        )
    }
}

@Composable
fun TalleresListScreen(
    estado: EstadoTalleres,
    onVolver: (() -> Unit)?,
    onAgregar: () -> Unit,
    onBuscarAsociado: () -> Unit = {},
    onFiltro: (FiltroOrigenTaller) -> Unit = {},
    onBusqueda: (String) -> Unit = {},
    onAbrirDetalle: (Taller) -> Unit,
    onFavorito: (Taller) -> Unit,
    onReintentar: () -> Unit,
    embebidoEnHistorial: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(if (embebidoEnHistorial) Color.Transparent else Fondo)
            .testTag("rf34.pantalla")
    ) {
        if (!embebidoEnHistorial) {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onVolver != null) {
                        IconButton(onClick = onVolver, modifier = Modifier.testTag("rf34.volver")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Azul)
                        }
                    }
                    Text(
                        "Talleres",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Azul
                    )
                    BotonAgregarTaller(onAgregar = onAgregar, enabled = !estado.ocupado)
                }
            }
        }
        when {
            estado.fase == FaseTalleres.CARGANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Celeste)
                Text("Cargando talleres…", Modifier.padding(16.dp), color = Azul)
            }
            estado.fase == FaseTalleres.ERROR -> Column(Modifier.padding(16.dp)) {
                Text(estado.mensaje ?: "Error", color = Azul)
                Button(onClick = onReintentar) { Text("Reintentar") }
            }
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (embebidoEnHistorial) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        BotonAgregarTaller(onAgregar = onAgregar, enabled = !estado.ocupado)
                    }
                }
                OutlinedTextField(
                    value = estado.busqueda,
                    onValueChange = onBusqueda,
                    modifier = Modifier.fillMaxWidth().testTag("rf34.buscar"),
                    placeholder = { Text("Buscar taller") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Celeste) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Celeste,
                        unfocusedBorderColor = Color(0xFFC5D3E3)
                    )
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).testTag("rf33.filtros"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FiltroOrigenTaller.entries.forEach { f ->
                        FilterChip(
                            selected = estado.filtro == f,
                            onClick = { onFiltro(f) },
                            label = { Text(f.etiqueta) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Azul,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("rf33.filtro.${f.api}")
                        )
                    }
                }
                OutlinedButton(
                    onClick = onBuscarAsociado,
                    modifier = Modifier.fillMaxWidth().testTag("rf33.buscar.asociado"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !estado.ocupado
                ) { Text("Buscar taller asociado a MyCar") }
                estado.mensaje?.let { Text(it, color = Celeste, modifier = Modifier.testTag("rf34.mensaje")) }
                val lista = estado.talleresFiltrados
                if (lista.isEmpty()) {
                    Text(
                        "Aún no tienes talleres registrados",
                        color = Azul,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("rf34.vacio")
                    )
                    Text("Agrega un taller manual o busca un asociado MyCar.", color = Color(0xFF6881A5))
                } else {
                    lista.forEach { t ->
                        TarjetaTaller(
                            t = t,
                            onClick = { onAbrirDetalle(t) },
                            onFavorito = { onFavorito(t) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaTaller(t: Taller, onClick: () -> Unit, onFavorito: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("rf34.taller.${t.id}"),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (t.esAsociado) VerdeAsociadoBg else AzulManualBg,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (t.esAsociado) Icons.Default.Store else Icons.Default.Build,
                        contentDescription = null,
                        tint = if (t.esAsociado) VerdeAsociado else Azul
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.nombre, fontWeight = FontWeight.SemiBold, color = Azul)
                BadgeOrigen(t.esAsociado)
                t.telefono?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6881A5))
                }
                t.direccion?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6881A5), maxLines = 1)
                }
            }
            IconButton(onClick = onFavorito, modifier = Modifier.testTag("rf37.favorito.accion.${t.id}")) {
                Icon(
                    if (t.favorito) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (t.favorito) "Quitar favorito" else "Marcar favorito",
                    tint = if (t.favorito) Estrella else Color(0xFF9AABB8),
                    modifier = Modifier.testTag("rf37.favorito.${t.id}")
                )
            }
        }
    }
}

@Composable
private fun BadgeOrigen(asociado: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (asociado) VerdeAsociadoBg else AzulManualBg
    ) {
        Text(
            if (asociado) "Asociado" else "Manual",
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (asociado) VerdeAsociado else Azul,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DetalleTallerScreen(
    taller: Taller,
    ocupado: Boolean,
    mensaje: String?,
    onVolver: () -> Unit,
    onEditar: () -> Unit,
    onFavorito: () -> Unit,
    onRetirar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmar by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(Fondo).testTag("rf34.detalle")) {
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onVolver) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Azul)
                }
                Text("Taller", fontWeight = FontWeight.Bold, color = Azul, style = MaterialTheme.typography.titleLarge)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = if (taller.esAsociado) VerdeAsociadoBg else AzulManualBg, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (taller.esAsociado) Icons.Default.Store else Icons.Default.Build,
                        contentDescription = null,
                        tint = if (taller.esAsociado) VerdeAsociado else Azul,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Text(taller.nombre, fontWeight = FontWeight.Bold, color = Azul, style = MaterialTheme.typography.titleMedium)
            Surface(shape = RoundedCornerShape(50), color = if (taller.esAsociado) VerdeAsociadoBg else AzulManualBg) {
                Text(
                    if (taller.esAsociado) "Asociado a MyCar · solo lectura" else "Manual",
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = if (taller.esAsociado) VerdeAsociado else Azul,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            mensaje?.let { Text(it, color = Celeste) }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 1.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    taller.telefono?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null, tint = Celeste)
                            Spacer(Modifier.width(10.dp))
                            Text(it, color = Azul)
                        }
                    }
                    taller.direccion?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = Celeste)
                            Spacer(Modifier.width(10.dp))
                            Text(it, color = Azul)
                        }
                    }
                    if (taller.esAsociado) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = Celeste)
                            Spacer(Modifier.width(10.dp))
                            Text("Datos verificados por MyCar", color = Color(0xFF6881A5))
                        }
                    }
                    taller.notas?.takeIf { it.isNotBlank() }?.let {
                        Text("Notas: $it", color = Color(0xFF6881A5))
                    }
                }
            }
            OutlinedButton(
                onClick = onFavorito,
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth().testTag("rf37.favorito.detalle"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (taller.favorito) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    null,
                    tint = Estrella
                )
                Spacer(Modifier.width(8.dp))
                Text(if (taller.favorito) "Quitar de favoritos" else "Marcar como favorito")
            }
            if (taller.editable) {
                Button(
                    onClick = onEditar,
                    enabled = !ocupado,
                    modifier = Modifier.fillMaxWidth().testTag("rf35.editar.detalle"),
                    colors = ButtonDefaults.buttonColors(containerColor = Azul),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Editar taller") }
            }
            OutlinedButton(
                onClick = { confirmar = true },
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth().testTag("rf36.eliminar.detalle"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text(if (taller.esAsociado) "Retirar de mis talleres" else "Eliminar taller")
            }
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = {
                Text(
                    if (taller.esAsociado) "¿Retirar de Mis talleres?" else "¿Eliminar taller?",
                    fontWeight = FontWeight.Bold,
                    color = Azul
                )
            },
            text = {
                Text(
                    if (taller.esAsociado)
                        "Solo se elimina el vínculo personal. El taller global de MyCar no se modifica."
                    else
                        "El taller dejará de estar disponible para nuevos mantenimientos."
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirmar = false; onRetirar() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.testTag("rf36.confirmar")
                ) { Text(if (taller.esAsociado) "Sí, retirar" else "Sí, eliminar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmar = false }, modifier = Modifier.testTag("rf36.cancelar")) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun FormularioTallerScreen(
    editando: Taller?,
    ocupado: Boolean,
    mensaje: String?,
    onCancelar: () -> Unit,
    onGuardar: (DatosTallerFormulario) -> Unit,
    onIrAsociado: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var modoAsociado by remember { mutableStateOf(false) }
    var nombre by remember(editando) { mutableStateOf(editando?.nombre.orEmpty()) }
    var telefono by remember(editando) { mutableStateOf(editando?.telefono.orEmpty()) }
    var correo by remember(editando) { mutableStateOf(editando?.correo.orEmpty()) }
    var direccion by remember(editando) { mutableStateOf(editando?.direccion.orEmpty()) }
    var notas by remember(editando) { mutableStateOf(editando?.notas.orEmpty()) }
    var tieneWhatsapp by remember(editando) { mutableStateOf(editando?.tieneWhatsapp == true) }
    var confirmarDescartar by remember { mutableStateOf(false) }
    var tocados by remember(editando) { mutableStateOf(setOf<String>()) }
    var intentoGuardar by remember(editando) { mutableStateOf(false) }

    val inicialNombre = editando?.nombre.orEmpty()
    val inicialTel = editando?.telefono.orEmpty()
    val inicialCorreo = editando?.correo.orEmpty()
    val inicialDir = editando?.direccion.orEmpty()
    val inicialNotas = editando?.notas.orEmpty()
    val inicialWa = editando?.tieneWhatsapp == true
    val hayCambios = nombre != inicialNombre || telefono != inicialTel || correo != inicialCorreo ||
        direccion != inicialDir || notas != inicialNotas || tieneWhatsapp != inicialWa

    LaunchedEffect(telefono) { if (telefono.isBlank()) tieneWhatsapp = false }

    val telDigitos = ValidadorFormularioTaller.digitosTelefono(telefono)
    val errores = ValidadorFormularioTaller.errores(nombre, telDigitos, correo, notas)

    fun mostrarError(campo: String) = intentoGuardar || campo in tocados

    Column(modifier.fillMaxSize().background(Fondo)) {
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (hayCambios) confirmarDescartar = true else onCancelar()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Azul)
                }
                Text(
                    if (editando == null) "Agregar taller" else "Editar taller",
                    fontWeight = FontWeight.Bold,
                    color = Azul,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (editando == null) {
                Row(
                    Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = !modoAsociado,
                        onClick = { modoAsociado = false },
                        label = { Text("Manual") },
                        modifier = Modifier.weight(1f).testTag("rf34.modo.manual"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Azul, selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = modoAsociado,
                        onClick = { modoAsociado = true; onIrAsociado() },
                        label = { Text("Asociado a MyCar") },
                        modifier = Modifier.weight(1f).testTag("rf34.modo.asociado"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Azul, selectedLabelColor = Color.White
                        )
                    )
                }
            }
            mensaje?.let { Text(it, color = Color(0xFFC62828)) }
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it; tocados = tocados + "nombre" },
                label = { Text("Nombre del taller *") },
                isError = mostrarError("nombre") && errores.containsKey("nombre"),
                supportingText = {
                    if (mostrarError("nombre")) errores["nombre"]?.let { Text(it) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().testTag("rf34.nombre"),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = telefono,
                onValueChange = { telefono = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(15); tocados = tocados + "telefono" },
                label = { Text("Teléfono de contacto (opcional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("rf34.telefono"),
                shape = RoundedCornerShape(12.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = tieneWhatsapp,
                    onCheckedChange = { if (telefono.isNotBlank()) tieneWhatsapp = it },
                    enabled = telefono.isNotBlank(),
                    modifier = Modifier.testTag("rf34.whatsapp")
                )
                Text("Este número también tiene WhatsApp", color = Azul)
            }
            OutlinedTextField(
                value = direccion,
                onValueChange = { direccion = it },
                label = { Text("Dirección (opcional)") },
                modifier = Modifier.fillMaxWidth().testTag("rf34.direccion"),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = correo,
                onValueChange = { correo = it },
                label = { Text("Correo (opcional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("rf34.correo"),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = notas,
                onValueChange = { notas = it },
                label = { Text("Notas (opcional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("rf34.notas"),
                shape = RoundedCornerShape(12.dp)
            )
        }
        Surface(color = Color.White, shadowElevation = 4.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        intentoGuardar = true
                        if (errores.isEmpty() && nombre.isNotBlank()) {
                            onGuardar(
                                DatosTallerFormulario(
                                    nombre.trim(),
                                    telDigitos.ifBlank { null },
                                    correo.ifBlank { null },
                                    direccion.ifBlank { null },
                                    notas.ifBlank { null },
                                    tieneWhatsapp && telDigitos.isNotBlank()
                                )
                            )
                        }
                    },
                    enabled = !ocupado && nombre.isNotBlank() && errores.isEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("rf34.guardar"),
                    colors = ButtonDefaults.buttonColors(containerColor = Azul),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (editando == null) "Guardar taller" else "Guardar cambios") }
                TextButton(
                    onClick = { if (hayCambios) confirmarDescartar = true else onCancelar() },
                    modifier = Modifier.fillMaxWidth().testTag("rf34.cancelar")
                ) { Text("Cancelar", color = Celeste) }
            }
        }
    }
    if (confirmarDescartar) {
        AlertDialog(
            onDismissRequest = { confirmarDescartar = false },
            title = { Text("¿Descartar cambios?", fontWeight = FontWeight.Bold, color = Azul) },
            text = { Text("Perderás los datos que aún no guardaste.") },
            confirmButton = {
                Button(
                    onClick = { confirmarDescartar = false; onCancelar() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.testTag("rf34.descartar")
                ) { Text("Descartar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmarDescartar = false }, modifier = Modifier.testTag("rf34.seguirEditando")) {
                    Text("Seguir editando")
                }
            }
        )
    }
}

@Composable
fun CatalogoAsociadoScreen(
    catalogo: List<TallerGlobal>,
    busqueda: String,
    ocupado: Boolean,
    mensaje: String?,
    onVolver: () -> Unit,
    onBuscar: (String) -> Unit,
    onVincular: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var q by remember(busqueda) { mutableStateOf(busqueda) }
    Column(modifier.fillMaxSize().background(Fondo).testTag("rf33.catalogo.asociado")) {
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onVolver) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Azul)
                }
                Text(
                    "Talleres asociados MyCar",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Azul
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth().testTag("rf33.catalogo.buscar"),
                placeholder = { Text("Buscar en catálogo MyCar") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Celeste) },
                trailingIcon = {
                    TextButton(onClick = { onBuscar(q) }, enabled = !ocupado) { Text("Buscar") }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            mensaje?.let { Text(it, color = Color(0xFFC62828)) }
            if (catalogo.isEmpty()) {
                Text("No hay talleres asociados disponibles.", color = Azul)
                Text(
                    "Un administrador MyCar debe registrar talleres oficiales en el catálogo.",
                    color = Color(0xFF6881A5)
                )
            } else {
                catalogo.forEach { g ->
                    Surface(
                        Modifier.fillMaxWidth().testTag("rf33.global.${g.id}"),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = VerdeAsociadoBg, modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Store, null, tint = VerdeAsociado)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(g.nombre, fontWeight = FontWeight.SemiBold, color = Azul)
                                    BadgeOrigen(asociado = true)
                                }
                            }
                            g.telefono?.let { Text(it, color = Color(0xFF6881A5)) }
                            g.direccion?.let { Text(it, color = Color(0xFF6881A5)) }
                            if (!g.contactoAutorizado) {
                                Text(
                                    "Contacto WhatsApp/llamada no autorizado aún",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF6881A5)
                                )
                            }
                            Button(
                                onClick = { onVincular(g.id) },
                                enabled = !ocupado,
                                colors = ButtonDefaults.buttonColors(containerColor = Azul),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("rf33.vincular.${g.id}")
                            ) { Text("Agregar a Mis talleres") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotonAgregarTaller(onAgregar: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onAgregar,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Azul),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.testTag("rf34.agregar")
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Agregar")
    }
}
