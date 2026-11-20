package com.mycar.app.features.historial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycar.app.features.historial.domain.*
import com.mycar.app.ui.MycarCabeceraApp
import androidx.compose.runtime.mutableIntStateOf
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FondoHistorial = Color(0xFFF0F5FA)
private val AzulHistorial = Color(0xFF12285F)
private val CelesteHistorial = Color(0xFF007E98)

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
    onRecargarKm: () -> Unit = {},
    onActualizarKm: ((Long) -> Unit)? = null,
    onCorregirKm: ((Long) -> Unit)? = null,
    vehiculoActivo: Boolean = false,
    talleresRepository: com.mycar.app.features.talleres.domain.TalleresRepository? = null,
    onNotificaciones: (() -> Unit)? = null,
    onPerfil: (() -> Unit)? = null,
    abrirEnTalleres: Boolean = false,
    onAbrirEnTalleresConsumido: () -> Unit = {}
) {
    var pestana by rememberSaveable {
        mutableStateOf(
            if (abrirEnTalleres) PestanaHistorial.TALLERES else PestanaHistorial.MANTENIMIENTOS
        )
    }
    var talleresSubpantalla by remember { mutableStateOf(false) }
    var solicitarAltaTalleres by remember { mutableIntStateOf(0) }
    var solicitarVolverTalleres by remember { mutableIntStateOf(0) }
    LaunchedEffect(abrirEnTalleres) {
        if (abrirEnTalleres) {
            pestana = PestanaHistorial.TALLERES
            onAbrirEnTalleresConsumido()
        }
    }
    val tituloSeccion = when {
        pestana == PestanaHistorial.TALLERES && talleresSubpantalla -> "Detalle taller"
        pestana == PestanaHistorial.TALLERES -> "Talleres"
        pestana == PestanaHistorial.KILOMETRAJE -> "Kilometraje"
        else -> "Mantenimientos"
    }

    Column(modifier.fillMaxSize().background(FondoHistorial).testTag("rf18.pantalla")) {
        MycarCabeceraApp(onNotificaciones = onNotificaciones, onPerfil = onPerfil)

        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Solo subpantallas (p. ej. detalle de taller) necesitan Volver; las pestañas
                // principales usan la barra inferior.
                if (pestana == PestanaHistorial.TALLERES && talleresSubpantalla) {
                    TextButton(
                        onClick = { solicitarVolverTalleres += 1 },
                        modifier = Modifier.testTag("rf18.volver")
                    ) { Text("Volver") }
                }
                Text(
                    tituloSeccion,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AzulHistorial
                )
                if (pestana == PestanaHistorial.MANTENIMIENTOS) {
                    Button(
                        onClick = { estado.seleccionado?.id?.let(onRegistrar) },
                        enabled = estado.puedeRegistrar && registroDisponible,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("rf18.registrar"),
                        colors = ButtonDefaults.buttonColors(containerColor = CelesteHistorial)
                    ) { Text("Registrar") }
                }
                if (pestana == PestanaHistorial.KILOMETRAJE && onActualizarKm != null) {
                    Button(
                        onClick = { estado.seleccionado?.id?.let(onActualizarKm) },
                        enabled = estado.seleccionado?.activo == true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("rf14.desdeHistorial"),
                        colors = ButtonDefaults.buttonColors(containerColor = CelesteHistorial)
                    ) { Text("Actualizar") }
                }
                if (pestana == PestanaHistorial.TALLERES && !talleresSubpantalla) {
                    com.mycar.app.features.talleres.ui.BotonAgregarTaller(
                        onAgregar = { solicitarAltaTalleres += 1 },
                        enabled = true
                    )
                }
            }
        }

        // Tabs del historial: ocultas en detalle/formulario de talleres.
        if (!(pestana == PestanaHistorial.TALLERES && talleresSubpantalla)) {
            Surface(color = Color.White, shadowElevation = 0.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        "Historial integral",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF6881A5),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    PestanasHistorial(pestana) { pestana = it }
                }
            }
        }

        when {
            pestana == PestanaHistorial.TALLERES && talleresRepository != null -> {
                com.mycar.app.features.talleres.ui.TalleresRoute(
                    repository = talleresRepository,
                    onVolver = onVolver,
                    embebidoEnHistorial = true,
                    onFormularioAbierto = { talleresSubpantalla = it },
                    solicitarAltaTick = solicitarAltaTalleres,
                    solicitarVolverTick = solicitarVolverTalleres,
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
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (estado.vehiculos.isNotEmpty()) {
                item { SelectorVehiculoHistorial(estado, onSeleccionar) }
            }
            estado.aviso?.let { item { MensajeHistorial("Vehículo no disponible", it) } }
            when (pestana) {
                PestanaHistorial.KILOMETRAJE -> item {
                    ContenidoKilometrajeHistorial(
                        estado = estado,
                        onRecargarKm = onRecargarKm,
                        onReintentar = onReintentar,
                        onOrdenar = onOrdenar,
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
                                color = CelesteHistorial
                            )
                            Text(
                                if (estado.fase == FaseHistorial.CARGANDO_VEHICULOS) "Cargando tus vehículos…"
                                else "Cargando el historial del vehículo…",
                                color = AzulHistorial
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
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${estado.contador} registros",
                                    Modifier.weight(1f).testTag("rf18.contador"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AzulHistorial
                                )
                                Text(
                                    "Más recientes primero",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AzulHistorial
                                )
                            }
                        }
                        if (estado.seleccionado?.activo == false) item {
                            MensajeHistorial(
                                "Vehículo inactivo",
                                "Puedes consultar su historial. El registro de mantenimientos está disponible para vehículos activos."
                            )
                        }
                        if (estado.mantenimientos.isEmpty()) item {
                            MensajeHistorial(
                                "Todavía no hay mantenimientos",
                                "Este vehículo tiene 0 registros de mantenimiento."
                            )
                        }
                        items(estado.mantenimientos, key = { it.id }) {
                            TarjetaMantenimientoHistorial(it, onVerDetalle = onVerDetalle)
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
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Vehículo", style = MaterialTheme.typography.labelMedium, color = AzulHistorial)
            Box {
                TextButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth().testTag("rf18.selector")) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(
                            estado.seleccionado?.nombre ?: "Selecciona un vehículo",
                            color = AzulHistorial,
                            fontWeight = FontWeight.SemiBold
                        )
                        estado.seleccionado?.let {
                            Text(
                                "${it.placa} · ${if (it.activo) "Activo" else "Inactivo"}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text("▾", color = CelesteHistorial)
                }
                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                    estado.vehiculos.forEach { v ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(v.nombre)
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
                        containerColor = AzulHistorial,
                        contentColor = Color.White
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
                    border = BorderStroke(1.dp, AzulHistorial.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = AzulHistorial
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                m.titulo,
                style = MaterialTheme.typography.titleMedium,
                color = AzulHistorial,
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
                color = AzulHistorial,
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
    onOrdenar: (Boolean) -> Unit,
    onCorregirKm: ((Long) -> Unit)? = null,
    vehiculoActivo: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Historial de kilometraje",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AzulHistorial
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = estado.ordenRecientes,
                onClick = { onOrdenar(true) },
                label = { Text("Más reciente") }
            )
            FilterChip(
                selected = !estado.ordenRecientes,
                onClick = { onOrdenar(false) },
                label = { Text("Más antiguo") }
            )
        }
        when (estado.faseKm) {
            FaseKilometrajeHistorial.IDLE, FaseKilometrajeHistorial.CARGANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = CelesteHistorial)
                Text("Cargando lecturas…", color = AzulHistorial)
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
                        colors = CardDefaults.cardColors(containerColor = Color.White)
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
                                        color = AzulHistorial
                                    )
                                    if (esUltima) {
                                        Text(
                                            if (yaCorregida) "Última lectura · Ya corregida" else "Última lectura",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CelesteHistorial
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
                                color = Color(0xFF6881A5)
                            )
                            l.fechaUtc?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6881A5))
                            }
                            if (l.kmAnteriorCorreccion != null) {
                                Text(
                                    "Corregido desde ${kilometrajeHistorial(l.kmAnteriorCorreccion)} km",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6881A5)
                                )
                                l.motivoCorreccion?.let {
                                    Text(
                                        "Motivo: $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF6881A5)
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
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AzulHistorial
            )
            Text(texto, style = MaterialTheme.typography.bodyMedium)
            acciones()
        }
    }
}
