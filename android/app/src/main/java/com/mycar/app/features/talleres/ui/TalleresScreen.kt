package com.mycar.app.features.talleres.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    /** Recibe la acción de alta personal. El padre la invoca en el clic; no hay efecto que se repita. */
    onAccionAlta: ((() -> Unit) -> Unit)? = null,
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
    DisposableEffect(vm) {
        onAccionAlta?.invoke(vm::abrirAlta)
        onDispose { onAccionAlta?.invoke { } }
    }
    when (estado.fase) {
        FaseTalleres.FORMULARIO -> FormularioTallerScreen(
            editando = estado.editando,
            ocupado = estado.ocupado,
            mensaje = estado.mensaje,
            onCancelar = vm::cancelarFormulario,
            onGuardar = vm::guardar,
            embebidoEnHistorial = embebidoEnHistorial,
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
                embebidoEnHistorial = embebidoEnHistorial,
                modifier = modifier
            )
        } ?: TalleresListScreen(
            estado = estado, onVolver = onVolver, embebidoEnHistorial = embebidoEnHistorial,
            onAgregar = vm::abrirAlta,
            onFiltro = vm::cambiarFiltro, onBusqueda = vm::cambiarBusqueda,
            onAbrirDetalle = vm::abrirDetalle, onFavorito = vm::alternarFavorito,
            onReintentar = vm::cargar, modifier = modifier
        )
        else -> TalleresListScreen(
            estado = estado,
            onVolver = onVolver,
            embebidoEnHistorial = embebidoEnHistorial,
            onAgregar = vm::abrirAlta,
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
                estado.mensaje?.let { Text(it, color = Celeste, modifier = Modifier.testTag("rf34.mensaje")) }
                val lista = estado.talleresFiltrados
                if (lista.isEmpty()) {
                    Text(
                        "Aún no tienes talleres en Mis talleres",
                        color = Azul,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("rf34.vacio")
                    )
                    Text(
                        "Usa Agregar para registrar un taller personal.",
                        color = Color(0xFF6881A5)
                    )
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
        Modifier.fillMaxWidth().testTag("rf34.taller.${t.id}"),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    ValidadorFormularioTaller.formatearTelefonoUi(t.telefono)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6881A5))
                    }
                    t.direccion?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6881A5), maxLines = 1)
                    }
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
    embebidoEnHistorial: Boolean = false,
    modifier: Modifier = Modifier
) {
    var confirmar by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(if (embebidoEnHistorial) Color.Transparent else Fondo).testTag("rf34.detalle")) {
        // Siempre statusBars: embebido oculta MycarCabeceraApp al abrir detalle (BUG-01).
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onVolver) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Azul)
                }
                Text("Detalle del taller", fontWeight = FontWeight.Bold, color = Azul, style = MaterialTheme.typography.titleLarge)
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
                    ValidadorFormularioTaller.formatearTelefonoUi(taller.telefono)?.let { telUi ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null, tint = Celeste)
                            Spacer(Modifier.width(10.dp))
                            Text(telUi, color = Azul)
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
    embebidoEnHistorial: Boolean = false,
    modifier: Modifier = Modifier
) {
    var nombre by remember(editando) {
        mutableStateOf(ValidadorFormularioTaller.normalizarNombre(editando?.nombre.orEmpty()))
    }
    var telefono by remember(editando) {
        mutableStateOf(ValidadorFormularioTaller.digitosTelefono(editando?.telefono.orEmpty()))
    }
    var correo by remember(editando) { mutableStateOf(editando?.correo.orEmpty()) }
    var direccion by remember(editando) {
        mutableStateOf(ValidadorFormularioTaller.normalizarDireccion(editando?.direccion.orEmpty()))
    }
    var notas by remember(editando) { mutableStateOf(editando?.notas.orEmpty()) }
    var tieneWhatsapp by remember(editando) { mutableStateOf(editando?.tieneWhatsapp == true) }
    var confirmarDescartar by remember { mutableStateOf(false) }
    var tocados by remember(editando) { mutableStateOf(setOf<String>()) }
    var intentoGuardar by remember(editando) { mutableStateOf(false) }

    LaunchedEffect(telefono) {
        if (telefono.length != 9 || !telefono.startsWith("9")) tieneWhatsapp = false
    }

    val errores = ValidadorFormularioTaller.errores(nombre, telefono, correo, direccion, notas)
    val puedeGuardar = !ocupado && ValidadorFormularioTaller.puedeGuardar(
        nombre, telefono, correo, direccion, notas, tieneWhatsapp, editando
    )
    val hayCambios = editando == null || ValidadorFormularioTaller.hayCambiosReales(
        nombre, telefono, correo, direccion, notas, tieneWhatsapp, editando
    )

    fun mostrarError(campo: String) = intentoGuardar || campo in tocados

    // Siempre statusBars: embebido oculta MycarCabeceraApp al abrir formulario (BUG-01).
    Column(
        modifier
            .fillMaxSize()
            .background(if (embebidoEnHistorial) Color.Transparent else Fondo)
    ) {
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (hayCambios) confirmarDescartar = true else onCancelar()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Azul)
                }
                Text(
                    if (editando == null) "Registrar taller" else "Editar taller",
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
            mensaje?.let { Text(it, color = Color(0xFFC62828)) }
            OutlinedTextField(
                value = nombre,
                onValueChange = {
                    nombre = it.uppercase()
                    tocados = tocados + "nombre"
                },
                label = { Text("Nombre del taller *") },
                placeholder = { Text("TALLER SAN MIGUEL") },
                isError = mostrarError("nombre") && errores.containsKey("nombre"),
                supportingText = {
                    if (mostrarError("nombre")) errores["nombre"]?.let { Text(it) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth().testTag("rf34.nombre"),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = telefono,
                onValueChange = { raw ->
                    // Prefijo +51 fijo en UI; solo se editan los 9 dígitos nacionales.
                    val digits = ValidadorFormularioTaller.digitosTelefono(raw).take(9)
                    telefono = digits
                    tocados = tocados + "telefono"
                },
                label = { Text("Celular de contacto (opcional)") },
                placeholder = { Text("987654321") },
                prefix = { Text("+51 ", color = Azul, fontWeight = FontWeight.SemiBold) },
                isError = mostrarError("telefono") && errores.containsKey("telefono"),
                supportingText = {
                    if (mostrarError("telefono")) errores["telefono"]?.let { Text(it) }
                    else Text("9 dígitos, debe empezar con 9", color = Color(0xFF6881A5))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("rf34.telefono"),
                shape = RoundedCornerShape(12.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val telValido = telefono.length == 9 && telefono.startsWith("9")
                Checkbox(
                    checked = tieneWhatsapp,
                    onCheckedChange = { if (telValido) tieneWhatsapp = it },
                    enabled = telValido,
                    modifier = Modifier.testTag("rf34.whatsapp")
                )
                Text("Este número también tiene WhatsApp", color = Azul)
            }
            OutlinedTextField(
                value = direccion,
                onValueChange = {
                    direccion = it.uppercase()
                    tocados = tocados + "direccion"
                },
                label = { Text("Dirección (opcional)") },
                placeholder = { Text("AV. LOS OLIVOS 123") },
                isError = mostrarError("direccion") && errores.containsKey("direccion"),
                supportingText = {
                    if (mostrarError("direccion")) errores["direccion"]?.let { Text(it) }
                },
                modifier = Modifier.fillMaxWidth().testTag("rf34.direccion"),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = correo,
                onValueChange = {
                    correo = it
                    tocados = tocados + "correo"
                },
                label = { Text("Correo (opcional)") },
                placeholder = { Text("taller@correo.com") },
                isError = mostrarError("correo") && errores.containsKey("correo"),
                supportingText = {
                    if (mostrarError("correo")) errores["correo"]?.let { Text(it) }
                },
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
                        if (!puedeGuardar) return@Button
                        val tel = ValidadorFormularioTaller.digitosTelefono(telefono)
                        onGuardar(
                            DatosTallerFormulario(
                                ValidadorFormularioTaller.normalizarNombre(nombre),
                                tel.ifBlank { null },
                                ValidadorFormularioTaller.normalizarCorreo(correo).ifBlank { null },
                                ValidadorFormularioTaller.normalizarDireccion(direccion).ifBlank { null },
                                ValidadorFormularioTaller.normalizarNotas(notas).ifBlank { null },
                                tieneWhatsapp && tel.isNotBlank() && tel.length <= 9
                            )
                        )
                    },
                    enabled = puedeGuardar,
                    modifier = Modifier.fillMaxWidth().testTag("rf34.guardar"),
                    colors = ButtonDefaults.buttonColors(containerColor = Azul),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (editando == null) "Guardar taller"
                        else "Guardar edición"
                    )
                }
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
                placeholder = { Text("Ej. PRUEBA o MyCar") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Celeste) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onBuscar(q) }
                )
            )
            Button(
                onClick = { onBuscar(q) },
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Azul),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (ocupado) "Buscando…" else "Buscar en catálogo") }
            TextButton(
                onClick = { q = ""; onBuscar("") },
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ver todos los oficiales") }
            mensaje?.let { Text(it, color = Color(0xFFC62828)) }
            if (ocupado && catalogo.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Celeste)
                Text("Cargando catálogo oficial…", color = Azul)
            } else if (catalogo.isEmpty()) {
                Text("No hay talleres en esta búsqueda.", color = Azul)
                Text("Prueba «Ver todos» o escribe PRUEBA.", color = Color(0xFF6881A5))
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
                                    if (g.contactoAutorizado) {
                                        Text(
                                            "WhatsApp autorizado",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF128C7E),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            ValidadorFormularioTaller.formatearTelefonoUi(g.telefono)?.let {
                                Text(it, color = Color(0xFF6881A5))
                            }
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
fun BotonAgregarTaller(onAgregar: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onAgregar,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Azul),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.testTag("rf34.agregar")
    ) {
        Text("+ Agregar")
    }
}
