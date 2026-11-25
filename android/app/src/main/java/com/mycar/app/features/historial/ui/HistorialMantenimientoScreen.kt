package com.mycar.app.features.historial.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mycar.app.features.historial.domain.*
import com.mycar.app.ui.MycarCabeceraApp
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.util.Locale


/** Vista real de producción; ejemplos de vehículos/registros viven únicamente en debug y pruebas. */
@Composable
fun HistorialMantenimientoScreen(
    estado: EstadoHistorial,
    onVolver: () -> Unit,
    onSeleccionar: (Long) -> Unit,
    onReintentar: () -> Unit,
    onActualizar: () -> Unit,
    onRegistrar: (Long) -> Unit,
    modifier: Modifier = Modifier,
    registroDisponible: Boolean = false,
    onIniciarSesion: (() -> Unit)? = null,
    onVerDetalle: ((Long) -> Unit)? = null,
    onOrdenar: (Boolean) -> Unit = {},
    onFiltrarTipo: (String?) -> Unit = {},
    onRecargarKm: () -> Unit = {},
    onActualizarKm: ((Long) -> Unit)? = null,
    onCorregirKm: ((Long) -> Unit)? = null,
    vehiculoActivo: Boolean = false,
    talleresRepository: com.mycar.app.features.talleres.domain.TalleresRepository? = null,
    onNotificaciones: (() -> Unit)? = null,
    onPerfil: (() -> Unit)? = null,
    abrirEnTalleres: Boolean = false,
    onAbrirEnTalleresConsumido: () -> Unit = {},
    avatarBytes: ByteArray? = null,
    iniciales: String? = null,
    /** Catálogo real de tipos (RF17/RF59). Los históricos ausentes del catálogo se suman en el desplegable. */
    tiposCatalogo: List<Pair<String, String>> = emptyList(),
    /** true = formulario/detalle taller a pantalla completa (oculta cabecera y nav inferior). */
    onFormularioTallerAbierto: (Boolean) -> Unit = {},
    /** RF26 — Exportar historial PDF (solo pestaña Mantenimientos). */
    onExportar: (() -> Unit)? = null
) {
    var pestana by rememberSaveable {
        mutableStateOf(
            if (abrirEnTalleres) PestanaHistorial.TALLERES else PestanaHistorial.MANTENIMIENTOS
        )
    }
    var talleresSubpantalla by remember { mutableStateOf(false) }
    // El botón llama una vez a abrirAlta. Un tick + LaunchedEffect se reejecutaba al
    // recomponer y el padre titulaba esa misma pantalla como "Detalle taller".
    var abrirAltaTaller by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(abrirEnTalleres) {
        if (abrirEnTalleres) {
            pestana = PestanaHistorial.TALLERES
            onAbrirEnTalleresConsumido()
        }
    }
    val shellVisible = !(pestana == PestanaHistorial.TALLERES && talleresSubpantalla)
    LaunchedEffect(shellVisible) {
        onFormularioTallerAbierto(!shellVisible)
    }
    val pestanaState = rememberUpdatedState(pestana)
    val deltaFiltro = remember { mutableFloatStateOf(0f) }
    val estadoLista = rememberLazyListState()
    val mostrarCriterios by remember {
        derivedStateOf {
            val enTope = !estadoLista.canScrollBackward
            enTope || deltaFiltro.floatValue > -24f
        }
    }
    val scrollFiltros = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (pestanaState.value != PestanaHistorial.MANTENIMIENTOS) return Offset.Zero
                val dy = consumed.y
                when {
                    dy > 0.5f -> deltaFiltro.floatValue = 0f
                    dy < -0.5f -> deltaFiltro.floatValue =
                        (deltaFiltro.floatValue + dy).coerceAtLeast(-80f)
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(pestana) {
        if (pestana == PestanaHistorial.MANTENIMIENTOS) deltaFiltro.floatValue = 0f
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("rf18.pantalla")) {
        if (shellVisible) {
            MycarCabeceraApp(
                onNotificaciones = onNotificaciones,
                onPerfil = onPerfil,
                avatarBytes = avatarBytes,
                iniciales = iniciales
            )
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Historial",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    when (pestana) {
                        PestanaHistorial.MANTENIMIENTOS -> {
                            Button(
                                onClick = { estado.seleccionado?.id?.let(onRegistrar) },
                                enabled = estado.puedeRegistrar && registroDisponible,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("rf18.registrar"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("+ Registrar", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        PestanaHistorial.TALLERES -> {
                            com.mycar.app.features.talleres.ui.BotonAgregarTaller(
                                onAgregar = { abrirAltaTaller?.invoke() },
                                enabled = abrirAltaTaller != null
                            )
                        }
                        PestanaHistorial.KILOMETRAJE -> Unit
                    }
                }
            }
            if (pestana != PestanaHistorial.TALLERES && estado.vehiculos.isNotEmpty()) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SelectorVehiculoHistorial(estado, onSeleccionar)
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 0.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    PestanasHistorial(pestana) { pestana = it }
                }
            }
            when (pestana) {
                PestanaHistorial.MANTENIMIENTOS -> {
                    AnimatedVisibility(
                        visible = estado.vehiculos.isNotEmpty() && mostrarCriterios,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        CriteriosFiltradoMantenimientos(
                            estado = estado,
                            tiposCatalogo = tiposCatalogo,
                            onFiltrarTipo = onFiltrarTipo,
                            onOrdenar = onOrdenar,
                            onExportar = onExportar,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                PestanaHistorial.KILOMETRAJE -> {
                    if (estado.faseKm == FaseKilometrajeHistorial.LISTO) {
                        OrdenHistorial(
                            recientes = estado.ordenRecientes,
                            onOrdenar = onOrdenar,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                PestanaHistorial.TALLERES -> Unit
            }
        }

        when {
            pestana == PestanaHistorial.TALLERES && talleresRepository != null -> {
                com.mycar.app.features.talleres.ui.TalleresRoute(
                    repository = talleresRepository,
                    onVolver = onVolver,
                    embebidoEnHistorial = true,
                    onFormularioAbierto = { talleresSubpantalla = it },
                    onAccionAlta = { abrirAltaTaller = it },
                    modifier = Modifier.weight(1f).testTag("rf34.enHistorial")
                )
            }
            pestana == PestanaHistorial.TALLERES -> {
                Column(
                    Modifier.weight(1f).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MensajeHistorial(
                        "Talleres no disponibles",
                        "No se pudo conectar el módulo de talleres. Cierra sesión, vuelve a entrar o reintenta."
                    )
                    onIniciarSesion?.let { Button(onClick = it) { Text("Reintentar sesión") } }
                }
            }
            else -> LazyColumn(
            Modifier.weight(1f).nestedScroll(scrollFiltros),
            state = estadoLista,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            estado.aviso?.let { item { MensajeHistorial("Vehículo no disponible", it) } }
            when (pestana) {
                PestanaHistorial.KILOMETRAJE -> item {
                    ContenidoKilometrajeHistorial(
                        estado = estado,
                        onRecargarKm = onRecargarKm,
                        onReintentar = onReintentar,
                        onCorregirKm = onCorregirKm,
                        vehiculoActivo = vehiculoActivo
                    )
                }
                PestanaHistorial.TALLERES -> { /* manejado arriba */ }
                PestanaHistorial.MANTENIMIENTOS -> when (estado.fase) {
                    FaseHistorial.SESION_REQUERIDA -> item {
                        MensajeHistorial(
                            "Tu historial, en un solo lugar",
                            "Inicia sesión para consultar los mantenimientos de tus vehículos."
                        ) {
                            onIniciarSesion?.let { Button(onClick = it) { Text("Iniciar sesión") } }
                        }
                    }
                    FaseHistorial.CARGANDO_VEHICULOS, FaseHistorial.CARGANDO_HISTORIAL -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LinearProgressIndicator(
                                Modifier.fillMaxWidth().testTag("rf18.cargando"),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                if (estado.fase == FaseHistorial.CARGANDO_VEHICULOS) "Cargando tus vehículos…"
                                else "Cargando el historial del vehículo…",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    FaseHistorial.SIN_VEHICULOS -> item {
                        MensajeHistorial(
                            "No tienes vehículos registrados",
                            "Aquí podrás consultar su historial cuando tengas un vehículo registrado."
                        )
                    }
                    FaseHistorial.SIN_SELECCION -> item {
                        MensajeHistorial(
                            "Elige un vehículo",
                            "Selecciona uno de tus vehículos para consultar su historial. Esta elección no cambia tu vehículo principal."
                        )
                    }
                    FaseHistorial.ERROR_VEHICULOS, FaseHistorial.ERROR_HISTORIAL -> item {
                        MensajeHistorial("No se pudo cargar el historial", estado.mensaje ?: "Inténtalo nuevamente") {
                            Button(onClick = onReintentar, modifier = Modifier.testTag("rf18.reintentar")) {
                                Text("Reintentar")
                            }
                        }
                    }
                    FaseHistorial.LISTO -> {
                        if (estado.seleccionado?.activo == false) item {
                            MensajeHistorial(
                                "Vehículo inactivo",
                                "Puedes consultar su historial. El registro de mantenimientos está disponible para vehículos activos."
                            )
                        }
                        if (estado.mantenimientos.isEmpty() && estado.filtroCodigoTipo == null) {
                            item {
                                EstadoVacioMantenimientos()
                            }
                        } else {
                            item {
                                Text(
                                    "${estado.contador} registros",
                                    Modifier.testTag("rf18.contador"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (estado.sinCoincidenciasFiltro) {
                                item {
                                    MensajeHistorial(
                                        "Sin coincidencias",
                                        "Ningún mantenimiento coincide con el filtro seleccionado."
                                    )
                                }
                            }
                            items(estado.visibles, key = { it.id }) {
                                TarjetaMantenimientoHistorial(it, onVerDetalle = onVerDetalle)
                            }
                        }
                    }
                }
            }
            if (pestana == PestanaHistorial.MANTENIMIENTOS &&
                estado.fase !in setOf(FaseHistorial.SESION_REQUERIDA, FaseHistorial.CARGANDO_VEHICULOS)
            ) {
                item {
                    TextButton(
                        onClick = onActualizar,
                        enabled = !estado.cargando,
                        modifier = Modifier.fillMaxWidth().testTag("rf18.actualizar")
                    ) { Text("Actualizar historial") }
                }
            }
        }
        }
    }
}

private enum class PestanaHistorial { MANTENIMIENTOS, KILOMETRAJE, TALLERES }

@Composable
private fun SelectorVehiculoHistorial(estado: EstadoHistorial, onSeleccionar: (Long) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(8.dp).size(18.dp)
                    )
                }
                Text("Seleccionar vehículo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
            Box {
                OutlinedButton(
                    onClick = { abierto = true },
                    modifier = Modifier.fillMaxWidth().testTag("rf18.selector"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(
                            estado.seleccionado?.nombre ?: "Seleccionar vehículo",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        estado.seleccionado?.let {
                            Text(
                                "${it.placa} · ${if (it.activo) "Activo" else "Inactivo"}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text("▾", color = MaterialTheme.colorScheme.secondary)
                }
                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                    estado.vehiculos.forEach { v ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(v.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${v.placa} · ${if (v.activo) "Activo" else "Inactivo"}${if (v.principal) " · Principal" else ""}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            onClick = { abierto = false; onSeleccionar(v.id) },
                            modifier = Modifier.testTag("rf18.vehiculo.${v.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CriteriosFiltradoMantenimientos(
    estado: EstadoHistorial,
    tiposCatalogo: List<Pair<String, String>>,
    onFiltrarTipo: (String?) -> Unit,
    onOrdenar: (Boolean) -> Unit,
    onExportar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuTipo by remember { mutableStateOf(false) }
    val opciones = remember(tiposCatalogo, estado.tiposFiltro) {
        val map = linkedMapOf<String, String>()
        tiposCatalogo.forEach { (codigo, nombre) ->
            if (codigo.isNotBlank()) map[codigo] = nombre.ifBlank { codigo }
        }
        estado.tiposFiltro.forEach { (codigo, nombre) ->
            if (codigo.isNotBlank() && codigo !in map) map[codigo] = nombre.ifBlank { codigo }
        }
        map.entries.sortedBy { it.value.lowercase() }.map { it.key to it.value }
    }
    val etiquetaTipo = estado.filtroCodigoTipo?.let { codigo ->
        opciones.firstOrNull { it.first == codigo }?.second ?: codigo
    } ?: "Todos"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tipo de mantenimiento", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(
                onClick = { menuTipo = true },
                modifier = Modifier.fillMaxWidth().testTag("rf18.filtro.tipo"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    etiquetaTipo,
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("▾", color = MaterialTheme.colorScheme.secondary)
            }
            DropdownMenu(expanded = menuTipo, onDismissRequest = { menuTipo = false }) {
                DropdownMenuItem(
                    text = { Text("Todos") },
                    onClick = { menuTipo = false; onFiltrarTipo(null) },
                    modifier = Modifier.testTag("rf18.filtro.todos")
                )
                opciones.forEach { (codigo, nombre) ->
                    DropdownMenuItem(
                        text = { Text(nombre, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { menuTipo = false; onFiltrarTipo(codigo) },
                        modifier = Modifier.testTag("rf18.filtro.$codigo")
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OrdenHistorial(
                recientes = estado.ordenRecientes,
                onOrdenar = onOrdenar,
                modifier = Modifier.weight(1f)
            )
            if (onExportar != null && estado.seleccionado != null) {
                OutlinedButton(
                    onClick = onExportar,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("rf26.exportar")
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exportar", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun OrdenHistorial(
    recientes: Boolean,
    onOrdenar: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Orden", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonOrden(
                texto = "Más reciente",
                seleccionado = recientes,
                onClick = { onOrdenar(true) },
                tag = "rf18.orden.reciente"
            )
            BotonOrden(
                texto = "Más antiguo",
                seleccionado = !recientes,
                onClick = { onOrdenar(false) },
                tag = "rf18.orden.antiguo"
            )
        }
    }
}

@Composable
private fun BotonOrden(texto: String, seleccionado: Boolean, onClick: () -> Unit, tag: String) {
    val forma = RoundedCornerShape(10.dp)
    val padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    if (seleccionado) {
        Button(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 40.dp).testTag(tag),
            shape = forma,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text(texto, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 40.dp).testTag(tag),
            shape = forma,
            contentPadding = padding,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) { Text(texto, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun EstadoVacioMantenimientos() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    Icons.Filled.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(16.dp).size(36.dp)
                )
            }
            Text(
                "Aún no tienes mantenimientos registrados para este vehículo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "Cuando registres un servicio, aparecerá aquí con su fecha y detalle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun PestanasHistorial(activa: PestanaHistorial, onCambiar: (PestanaHistorial) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            PestanaHistorial.MANTENIMIENTOS to ("Mantenimientos" to "rf18.tab.mant"),
            PestanaHistorial.KILOMETRAJE to ("Kilometraje" to "rf18.tab.km"),
            PestanaHistorial.TALLERES to ("Talleres" to "rf18.tab.talleres")
        ).forEach { (pestana, etiqueta) ->
            val seleccionada = pestana == activa
            if (seleccionada) {
                Button(
                    onClick = { onCambiar(pestana) },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag(etiqueta.second),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        etiqueta.first,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onCambiar(pestana) },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag(etiqueta.second),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        etiqueta.first,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaMantenimientoHistorial(
    m: MantenimientoHistorial,
    onVerDetalle: ((Long) -> Unit)? = null
) {
    val realizado = m.estado == "REALIZADO"
    Card(
        Modifier.fillMaxWidth().testTag("rf18.registro.${m.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                m.titulo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (realizado) Color(0xFFDBF8E7) else Color(0xFFFCE4E4)
                ) {
                    Text(
                        if (realizado) "Realizado" else "Anulado",
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = if (realizado) Color(0xFF146943) else Color(0xFF943535),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (m.codigoTipo == "OTRO") Text("Otro", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "${m.fecha.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))} · ${kilometrajeHistorial(m.kmServicio)} km",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (m.costo == null) "Costo: No registrado"
                else "Costo: S/ ${m.costo.setScale(2, RoundingMode.UNNECESSARY).toPlainString()}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            m.taller?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            val puedeDetalle = onVerDetalle != null
            TextButton(
                onClick = { onVerDetalle?.invoke(m.id) },
                enabled = puedeDetalle,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.testTag("rf18.detalle.${m.id}")
            ) { Text(if (puedeDetalle) "Ver detalle" else "Ver detalle · Próximamente") }
        }
    }
}

@Composable
private fun ContenidoKilometrajeHistorial(
    estado: EstadoHistorial,
    onRecargarKm: () -> Unit,
    onReintentar: () -> Unit,
    onCorregirKm: ((Long) -> Unit)? = null,
    vehiculoActivo: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Historial de kilometraje",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (estado.seleccionado == null) {
            MensajeHistorial("Elige un vehículo", "Selecciona un vehículo para ver sus lecturas de kilometraje.")
            return
        }
        if (estado.seleccionado?.activo == false) {
            MensajeHistorial(
                "Vehículo inactivo",
                "Puedes consultar sus lecturas. Actualizar kilometraje está disponible solo para vehículos activos."
            )
        }
        when (estado.faseKm) {
            FaseKilometrajeHistorial.IDLE, FaseKilometrajeHistorial.CARGANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
                Text("Cargando lecturas…", color = MaterialTheme.colorScheme.onSurface)
            }
            FaseKilometrajeHistorial.ERROR -> {
                MensajeHistorial("No se pudo cargar el kilometraje", estado.mensajeKm ?: "Inténtalo de nuevo") {
                    Button(onClick = onReintentar) { Text("Reintentar") }
                }
            }
            FaseKilometrajeHistorial.VACIO -> {
                MensajeHistorial(
                    "Sin registros",
                    estado.mensajeKm ?: "Todavía no hay lecturas para este vehículo."
                )
            }
            FaseKilometrajeHistorial.LISTO -> {
                val idUltima = estado.lecturasKm.maxWithOrNull(
                    compareBy<LecturaKilometrajeHistorial>({ it.fechaUtc.orEmpty() }, { it.id })
                )?.id
                estado.lecturasKm.forEach { l ->
                    var menuAbierto by remember(l.id) { mutableStateOf(false) }
                    val esUltima = l.id == idUltima
                    val yaCorregida = l.kmAnteriorCorreccion != null
                    val puedeCorregir = esUltima && !yaCorregida && vehiculoActivo &&
                        onCorregirKm != null && estado.lecturasKm.size >= 2
                    Card(
                        Modifier.fillMaxWidth().testTag("rf15.lectura.${l.id}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${kilometrajeHistorial(l.km)} km",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (esUltima) {
                                        Text(
                                            if (yaCorregida) "Última lectura · Ya corregida" else "Última lectura",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                if (puedeCorregir) {
                                    Box {
                                        TextButton(
                                            onClick = { menuAbierto = true },
                                            modifier = Modifier.testTag("rf16.menu.${l.id}")
                                        ) { Text("⋯") }
                                        DropdownMenu(
                                            expanded = menuAbierto,
                                            onDismissRequest = { menuAbierto = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Corregir kilometraje") },
                                                onClick = {
                                                    menuAbierto = false
                                                    onCorregirKm?.invoke(l.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                "Origen: ${etiquetaOrigenKm(l.origen)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            l.fechaUtc?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (l.kmAnteriorCorreccion != null) {
                                Text(
                                    "Corregido desde ${kilometrajeHistorial(l.kmAnteriorCorreccion)} km",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                l.motivoCorreccion?.let {
                                    Text(
                                        "Motivo: $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onRecargarKm, modifier = Modifier.fillMaxWidth().testTag("rf15.actualizar")) {
                    Text("Actualizar lecturas")
                }
            }
        }
    }
}

private fun kilometrajeHistorial(km: Number): String = DecimalFormat(
    "#,##0.##",
    DecimalFormatSymbols(Locale.ROOT).apply { groupingSeparator = ' ' }
).format(km)

private fun etiquetaOrigenKm(origen: String): String = when (origen) {
    "INICIAL" -> "Registro inicial"
    "ACTUALIZACION" -> "Actualización"
    else -> origen
}

@Composable
private fun MensajeHistorial(titulo: String, texto: String, acciones: @Composable () -> Unit = {}) {
    OutlinedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(texto, style = MaterialTheme.typography.bodyMedium)
            acciones()
        }
    }
}
