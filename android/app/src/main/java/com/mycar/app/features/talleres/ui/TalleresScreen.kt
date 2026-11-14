package com.mycar.app.features.talleres.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
            estado.fase == FaseTalleres.FORMULARIO || estado.fase == FaseTalleres.CATALOGO_ASOCIADO
        )
    }
    when (estado.fase) {
        FaseTalleres.FORMULARIO -> FormularioTallerScreen(
            editando = estado.editando,
            ocupado = estado.ocupado,
            mensaje = estado.mensaje,
            onCancelar = vm::cancelarFormulario,
            onGuardar = vm::guardar,
            modifier = modifier
        )
        FaseTalleres.CATALOGO_ASOCIADO -> CatalogoAsociadoScreen(
            catalogo = estado.catalogo,
            ocupado = estado.ocupado,
            mensaje = estado.mensaje,
            onVolver = vm::cancelarFormulario,
            onVincular = vm::vincularAsociado,
            modifier = modifier
        )
        else -> TalleresListScreen(
            estado = estado,
            onVolver = onVolver,
            embebidoEnHistorial = embebidoEnHistorial,
            onAgregar = vm::abrirAlta,
            onBuscarAsociado = vm::abrirCatalogoAsociado,
            onFiltro = vm::cambiarFiltro,
            onEditar = vm::abrirEdicion,
            onRetirar = vm::retirar,
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
    onEditar: (Taller) -> Unit,
    onRetirar: (Taller) -> Unit,
    onFavorito: (Taller) -> Unit,
    onReintentar: () -> Unit,
    embebidoEnHistorial: Boolean = false,
    modifier: Modifier = Modifier
) {
    var confirmar by remember { mutableStateOf<Taller?>(null) }
    Column(modifier.fillMaxSize().background(if (embebidoEnHistorial) Color.Transparent else Fondo).testTag("rf34.pantalla")) {
        if (!embebidoEnHistorial) {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onVolver != null) {
                        TextButton(onClick = onVolver, modifier = Modifier.testTag("rf34.volver")) { Text("Volver") }
                    }
                    Text(
                        "Talleres",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Azul
                    )
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
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).testTag("rf33.filtros"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FiltroOrigenTaller.entries.forEach { f ->
                        FilterChip(
                            selected = estado.filtro == f,
                            onClick = { onFiltro(f) },
                            label = { Text(f.etiqueta) },
                            modifier = Modifier.testTag("rf33.filtro.${f.api}")
                        )
                    }
                }
                estado.mensaje?.let { Text(it, color = Celeste, modifier = Modifier.testTag("rf34.mensaje")) }
                if (estado.talleres.isEmpty()) {
                    Text(
                        "Aún no tienes talleres registrados",
                        color = Azul,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("rf34.vacio")
                    )
                    Text("Agrega un taller manual o busca un asociado MyCar.", color = Color(0xFF6881A5))
                } else {
                    estado.talleres.forEach { t ->
                        TarjetaTaller(
                            t = t,
                            onEditar = { onEditar(t) },
                            onFavorito = { onFavorito(t) },
                            onEliminar = { confirmar = t }
                        )
                    }
                }
            }
        }
        Surface(color = Color.White, shadowElevation = 4.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAgregar,
                    modifier = Modifier.fillMaxWidth().testTag("rf34.agregar"),
                    colors = ButtonDefaults.buttonColors(containerColor = Azul),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !estado.ocupado
                ) { Text("Agregar taller") }
                OutlinedButton(
                    onClick = onBuscarAsociado,
                    modifier = Modifier.fillMaxWidth().testTag("rf33.buscar.asociado"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !estado.ocupado
                ) { Text("Buscar taller asociado") }
            }
        }
    }
    confirmar?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = {
                Text(
                    if (t.esAsociado) "¿Retirar de Mis talleres?" else "¿Eliminar taller?",
                    fontWeight = FontWeight.Bold,
                    color = Azul
                )
            },
            text = {
                Text(
                    if (t.esAsociado)
                        "Solo se elimina el vínculo personal. El taller global de MyCar no se modifica y el historial se conserva."
                    else
                        "El taller dejará de estar disponible para nuevos mantenimientos, " +
                            "pero los mantenimientos históricos conservarán sus asociaciones."
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirmar = null; onRetirar(t) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.testTag("rf36.confirmar")
                ) { Text(if (t.esAsociado) "Sí, retirar" else "Sí, eliminar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmar = null }, modifier = Modifier.testTag("rf36.cancelar")) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun TarjetaTaller(
    t: Taller,
    onEditar: () -> Unit,
    onFavorito: () -> Unit,
    onEliminar: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth().testTag("rf34.taller.${t.id}"),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.nombre, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Azul)
                if (t.favorito) {
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFFE2F5FF)) {
                        Text(
                            "Favorito",
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp).testTag("rf37.favorito.${t.id}"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Celeste
                        )
                    }
                }
                Box {
                    TextButton(onClick = { menu = true }, modifier = Modifier.testTag("rf34.menu.${t.id}")) {
                        Text("⋮")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (t.editable) {
                            DropdownMenuItem(
                                text = { Text("Editar") },
                                onClick = { menu = false; onEditar() },
                                modifier = Modifier.testTag("rf35.editar.${t.id}")
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (t.favorito) "Quitar de favoritos" else "Marcar como favorito") },
                            onClick = { menu = false; onFavorito() },
                            modifier = Modifier.testTag("rf37.favorito.accion.${t.id}")
                        )
                        DropdownMenuItem(
                            text = { Text(if (t.esAsociado) "Retirar" else "Eliminar") },
                            onClick = { menu = false; onEliminar() },
                            modifier = Modifier.testTag("rf36.eliminar.${t.id}")
                        )
                    }
                }
            }
            Text(
                if (t.esAsociado) "Asociado MyCar · solo lectura" else "Manual",
                style = MaterialTheme.typography.labelSmall,
                color = Celeste
            )
            t.telefono?.let {
                Text(
                    if (t.tieneWhatsapp) "$it · WhatsApp" else it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6881A5)
                )
            }
            t.correo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6881A5)) }
            t.direccion?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6881A5)) }
        }
    }
}

@Composable
fun FormularioTallerScreen(
    editando: Taller?,
    ocupado: Boolean,
    mensaje: String?,
    onCancelar: () -> Unit,
    onGuardar: (DatosTallerFormulario) -> Unit,
    modifier: Modifier = Modifier
) {
    var nombre by remember(editando) { mutableStateOf(editando?.nombre.orEmpty()) }
    var telefono by remember(editando) { mutableStateOf(editando?.telefono.orEmpty()) }
    var correo by remember(editando) { mutableStateOf(editando?.correo.orEmpty()) }
    var direccion by remember(editando) { mutableStateOf(editando?.direccion.orEmpty()) }
    var notas by remember(editando) { mutableStateOf(editando?.notas.orEmpty()) }
    var tieneWhatsapp by remember(editando) { mutableStateOf(editando?.tieneWhatsapp == true) }
    var confirmarDescartar by remember { mutableStateOf(false) }
    var avisoSinCambios by remember { mutableStateOf(false) }
    // No mostrar errores de campo hasta que el usuario toque el campo o intente guardar.
    var tocados by remember(editando) { mutableStateOf(setOf<String>()) }
    var intentoGuardar by remember(editando) { mutableStateOf(false) }

    val inicialNombre = editando?.nombre.orEmpty()
    val inicialTel = editando?.telefono.orEmpty()
    val inicialCorreo = editando?.correo.orEmpty()
    val inicialDir = editando?.direccion.orEmpty()
    val inicialNotas = editando?.notas.orEmpty()
    val inicialWa = editando?.tieneWhatsapp == true

    val dirty = nombre != inicialNombre
        || telefono != inicialTel
        || correo != inicialCorreo
        || direccion != inicialDir
        || notas != inicialNotas
        || tieneWhatsapp != inicialWa

    LaunchedEffect(telefono) {
        if (telefono.isBlank()) tieneWhatsapp = false
    }

    val errores = ValidadorFormularioTaller.errores(nombre, telefono, correo, notas)
    fun errorVisible(campo: String): String? =
        if (intentoGuardar || campo in tocados) errores[campo] else null
    val puede = ValidadorFormularioTaller.puedeGuardar(
        nombre, telefono, correo, notas, editando,
        inicialNombre, inicialTel, inicialCorreo, inicialDir, inicialNotas, direccion
    )

    fun intentarSalir() {
        if (dirty) confirmarDescartar = true else onCancelar()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Fondo)
            .testTag("rf34.formulario")
    ) {
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { intentarSalir() }, modifier = Modifier.testTag("rf34.cancelar")) {
                    Text("Cancelar")
                }
                Text(
                    if (editando == null) "Agregar taller" else "Editar taller",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Azul
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = nombre,
                onValueChange = {
                    nombre = it.uppercase()
                    tocados = tocados + "nombre"
                    avisoSinCambios = false
                },
                label = { Text("Nombre del taller *") },
                isError = errorVisible("nombre") != null,
                supportingText = { errorVisible("nombre")?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth().testTag("rf34.nombre"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = telefono,
                onValueChange = {
                    telefono = ValidadorFormularioTaller.digitosTelefono(it)
                    tocados = tocados + "telefono"
                    avisoSinCambios = false
                },
                label = { Text("Teléfono (9 dígitos)") },
                isError = errorVisible("telefono") != null,
                supportingText = {
                    Text(errorVisible("telefono") ?: "${telefono.length}/9")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().testTag("rf34.telefono"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Row(
                Modifier.fillMaxWidth().testTag("rf33.whatsapp"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = tieneWhatsapp,
                    onCheckedChange = {
                        if (telefono.isNotBlank()) {
                            tieneWhatsapp = it
                            avisoSinCambios = false
                        }
                    },
                    enabled = telefono.isNotBlank() && !ocupado
                )
                Text("Este número también tiene WhatsApp", color = Azul)
            }
            OutlinedTextField(
                value = correo,
                onValueChange = {
                    correo = it.lowercase()
                    tocados = tocados + "correo"
                    avisoSinCambios = false
                },
                label = { Text("Correo electrónico") },
                isError = errorVisible("correo") != null,
                supportingText = { errorVisible("correo")?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().testTag("rf34.correo"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = direccion,
                onValueChange = { direccion = it.uppercase(); avisoSinCambios = false },
                label = { Text("Dirección") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth().testTag("rf34.direccion"),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = notas,
                onValueChange = {
                    notas = it.uppercase()
                    tocados = tocados + "notas"
                    avisoSinCambios = false
                },
                label = { Text("Notas") },
                isError = errorVisible("notas") != null,
                supportingText = {
                    Text(errorVisible("notas") ?: "${notas.codePointCount(0, notas.length)}/500")
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth().testTag("rf34.notas"),
                minLines = 3,
                shape = RoundedCornerShape(12.dp)
            )
            if (avisoSinCambios) {
                Text(
                    "No has realizado ningún cambio",
                    color = Color(0xFFC62828),
                    modifier = Modifier.testTag("rf35.sinCambios")
                )
            }
            mensaje?.let {
                Text(it, color = Color(0xFFC62828), modifier = Modifier.testTag("rf34.form.mensaje"))
            }
        }
        Surface(color = Color.White, shadowElevation = 4.dp) {
            Button(
                onClick = {
                    intentoGuardar = true
                    if (editando != null && !dirty) {
                        avisoSinCambios = true
                        return@Button
                    }
                    if (!puede) return@Button
                    onGuardar(
                        DatosTallerFormulario(
                            nombre = nombre.trim(),
                            telefono = telefono.ifBlank { null },
                            correo = correo.trim().ifBlank { null },
                            direccion = direccion.trim().ifBlank { null },
                            notas = notas.trim().ifBlank { null },
                            tieneWhatsapp = tieneWhatsapp && telefono.isNotBlank()
                        )
                    )
                },
                // Deshabilitado sin nombre; el mensaje de error solo aparece tras tocar o intentar guardar.
                enabled = !ocupado && nombre.isNotBlank() && errores.isEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("rf34.guardar"),
                colors = ButtonDefaults.buttonColors(containerColor = Azul),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (editando == null) "Registrar taller" else "Guardar cambios")
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
                OutlinedButton(
                    onClick = { confirmarDescartar = false },
                    modifier = Modifier.testTag("rf34.seguirEditando")
                ) { Text("Seguir editando") }
            }
        )
    }
}

@Composable
fun CatalogoAsociadoScreen(
    catalogo: List<TallerGlobal>,
    ocupado: Boolean,
    mensaje: String?,
    onVolver: () -> Unit,
    onVincular: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Fondo)
            .testTag("rf33.catalogo.asociado")
    ) {
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onVolver) { Text("Volver") }
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
            mensaje?.let { Text(it, color = Color(0xFFC62828)) }
            if (catalogo.isEmpty()) {
                Text("No hay talleres asociados disponibles.", color = Azul)
            } else {
                catalogo.forEach { g ->
                    Surface(
                        Modifier.fillMaxWidth().testTag("rf33.global.${g.id}"),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        shadowElevation = 1.dp
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(g.nombre, fontWeight = FontWeight.SemiBold, color = Azul)
                            g.telefono?.let { Text(it, color = Color(0xFF6881A5)) }
                            g.direccion?.let { Text(it, color = Color(0xFF6881A5)) }
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
