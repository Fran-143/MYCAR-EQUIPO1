@file:OptIn(ExperimentalMaterial3Api::class)

package com.mycar.app.features.historial.export

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.mycar.app.features.fotos.domain.FotoPersonalizadaRepository
import com.mycar.app.features.fotos.ui.ImagenVehiculoPredeterminada
import com.mycar.app.features.fotos.ui.MiniaturaFotoVehiculo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Navy = Color(0xFF003366)
private val DoradoPremium = Color(0xFFC9A227)
private val VerdeOk = Color(0xFFE8F5E9)
private val VerdeTexto = Color(0xFF2E7D32)
private val Lima = ZoneId.of("America/Lima")
private val DfUi = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun ExportarHistorialRoute(
    idVehiculo: Long,
    repository: ExportarHistorialRepository,
    onVolver: () -> Unit,
    onVerPlanes: () -> Unit,
    onIniciarSesion: () -> Unit,
    fotosRepository: FotoPersonalizadaRepository? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var etapa by remember(idVehiculo) { mutableStateOf("carga") }
    var contexto by remember { mutableStateOf<ContextoExportDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var desde by remember { mutableStateOf<LocalDate?>(null) }
    var hasta by remember { mutableStateOf<LocalDate?>(null) }
    var tipoId by remember { mutableStateOf<Long?>(null) }
    var incluirAnulados by remember { mutableStateOf(false) }
    var generando by remember { mutableStateOf(false) }
    var archivo by remember { mutableStateOf<File?>(null) }
    var avisoWhatsApp by remember { mutableStateOf<String?>(null) }
    var dialogoPremium by remember { mutableStateOf(false) }

    fun limpiarYVolver() {
        archivo = null
        ExportarHistorialPdfCache.limpiar(context)
        onVolver()
    }

    BackHandler {
        when (etapa) {
            "preview" -> {
                archivo = null
                ExportarHistorialPdfCache.limpiar(context)
                etapa = "form"
            }
            else -> limpiarYVolver()
        }
    }

    DisposableEffect(Unit) {
        onDispose { ExportarHistorialPdfCache.limpiar(context) }
    }

    LaunchedEffect(idVehiculo) {
        etapa = "carga"
        error = null
        runCatching { repository.contexto(idVehiculo) }
            .onSuccess { ctx ->
                contexto = ctx
                if (!ctx.premium) {
                    dialogoPremium = true
                    etapa = "form"
                } else {
                    etapa = "form"
                }
            }
            .onFailure { e ->
                when {
                    e is FalloExportarHistorial && e.codigo == "SESION_REQUERIDA" -> onIniciarSesion()
                    e is FalloExportarHistorial && e.codigo == "PREMIUM_REQUERIDO" -> {
                        dialogoPremium = true
                        etapa = "form"
                    }
                    else -> {
                        error = e.message ?: "No se pudo abrir la exportación."
                        etapa = "error"
                    }
                }
            }
    }

    if (dialogoPremium) {
        ModalBeneficioPremium(
            onVerPlanes = {
                dialogoPremium = false
                onVerPlanes()
            },
            onCancelar = {
                dialogoPremium = false
                limpiarYVolver()
            }
        )
    }

    when (etapa) {
        "carga" -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Navy)
        }
        "error" -> Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Exportar historial", color = Navy, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = ::limpiarYVolver) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                )
            }
        ) { pad ->
            Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(error ?: "Error", color = Color(0xFFC62828))
                OutlinedButton(onClick = ::limpiarYVolver) { Text("Volver") }
            }
        }
        "preview" -> VistaPreviaExport(
            archivo = archivo,
            aviso = avisoWhatsApp,
            onCerrarAviso = { avisoWhatsApp = null },
            onVolver = {
                archivo = null
                ExportarHistorialPdfCache.limpiar(context)
                etapa = "form"
            },
            onWhatsApp = {
                val f = archivo
                if (f == null || !f.exists()) {
                    avisoWhatsApp = "No se pudo leer el PDF temporal."
                    return@VistaPreviaExport
                }
                avisoWhatsApp = compartirSoloWhatsApp(context, f)
            },
            modifier = modifier
        )
        else -> FormularioExport(
            contexto = contexto,
            fotosRepository = fotosRepository,
            desde = desde,
            hasta = hasta,
            tipoId = tipoId,
            incluirAnulados = incluirAnulados,
            generando = generando,
            error = error,
            onDesde = { d ->
                desde = d
                val h = hasta
                if (h != null && (h.isBefore(d) || h.isAfter(d.plusMonths(6)) || h.isAfter(hoyLima()))) {
                    hasta = null
                }
            },
            onHasta = { hasta = it },
            onTipo = { tipoId = it },
            onAnulados = { incluirAnulados = it },
            onVolver = ::limpiarYVolver,
            onGenerar = {
                if (generando) return@FormularioExport
                val d = desde
                val h = hasta
                if (d == null || h == null) {
                    error = "Selecciona las fechas Desde y Hasta."
                    return@FormularioExport
                }
                generando = true
                error = null
                scope.launch {
                    val resultado = withContext(Dispatchers.IO) {
                        runCatching {
                            repository.generarPdf(
                                idVehiculo,
                                d.toString(),
                                h.toString(),
                                tipoId,
                                incluirAnulados
                            )
                        }
                    }
                    generando = false
                    resultado.onSuccess { (bytes, nombre) ->
                        val f = ExportarHistorialPdfCache.guardar(context, bytes, nombre)
                        archivo = f
                        etapa = "preview"
                    }.onFailure { e ->
                        when {
                            e is FalloExportarHistorial && e.codigo == "SESION_REQUERIDA" -> onIniciarSesion()
                            e is FalloExportarHistorial && e.codigo == "PREMIUM_REQUERIDO" -> {
                                dialogoPremium = true
                            }
                            else -> error = e.message ?: "No se pudo generar el PDF."
                        }
                    }
                }
            },
            modifier = modifier
        )
    }
}

private fun hoyLima(): LocalDate = LocalDate.now(Lima)

@Composable
private fun ModalBeneficioPremium(
    onVerPlanes: () -> Unit,
    onCancelar: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onCancelar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(20.dp),
            color = scheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = DoradoPremium.copy(alpha = 0.16f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WorkspacePremium,
                        contentDescription = null,
                        tint = DoradoPremium,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Text(
                    "Beneficio Premium",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    "La exportación de historiales en PDF está disponible para usuarios con un plan Premium activo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onVerPlanes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
                ) {
                    Text("Ver planes", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                TextButton(onClick = onCancelar) {
                    Text("Cancelar", color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** RF26: solo WhatsApp / WhatsApp Business; sin chooser genérico. */
private fun compartirSoloWhatsApp(context: android.content.Context, archivo: File): String? {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "MyCar PDF", uri)
            setPackage(pkg)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            return try {
                context.startActivity(intent)
                null
            } catch (_: ActivityNotFoundException) {
                continue
            }
        }
    }
    return "No se pudo abrir WhatsApp para compartir el PDF."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioExport(
    contexto: ContextoExportDto?,
    fotosRepository: FotoPersonalizadaRepository?,
    desde: LocalDate?,
    hasta: LocalDate?,
    tipoId: Long?,
    incluirAnulados: Boolean,
    generando: Boolean,
    error: String?,
    onDesde: (LocalDate) -> Unit,
    onHasta: (LocalDate) -> Unit,
    onTipo: (Long?) -> Unit,
    onAnulados: (Boolean) -> Unit,
    onVolver: () -> Unit,
    onGenerar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val hoy = remember { hoyLima() }
    val periodoValido = desde != null && hasta != null
            && !hasta.isBefore(desde)
            && !hasta.isAfter(desde.plusMonths(6))
            && !desde.isAfter(hoy)
            && !hasta.isAfter(hoy)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Exportar historial", color = scheme.primary, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = scheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface)
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = scheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(64.dp)) {
                        if (fotosRepository != null && contexto != null) {
                            MiniaturaFotoVehiculo(
                                idVehiculo = contexto.idVehiculo,
                                tieneFoto = true,
                                fotos = fotosRepository,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = { ImagenVehiculoPredeterminada(Modifier.fillMaxSize()) }
                            )
                        } else {
                            ImagenVehiculoPredeterminada(Modifier.fillMaxSize())
                        }
                    }
                    Column {
                        Text(
                            listOfNotNull(contexto?.marca, contexto?.modelo).joinToString(" ")
                                .ifBlank { "Vehículo" },
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        Text(contexto?.placa.orEmpty(), color = scheme.onSurfaceVariant)
                    }
                }
            }

            CampoFechaExport(
                etiqueta = "Desde",
                valor = desde,
                habilitado = true,
                min = null,
                max = hoy,
                tituloDialogo = "Selecciona fecha Desde",
                onElegir = onDesde
            )
            CampoFechaExport(
                etiqueta = "Hasta",
                valor = hasta,
                habilitado = desde != null,
                min = desde,
                max = desde?.let { minOf(it.plusMonths(6), hoy) } ?: hoy,
                tituloDialogo = "Selecciona fecha Hasta",
                onElegir = onHasta
            )

            if (periodoValido && desde != null && hasta != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VerdeOk,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Período válido", fontWeight = FontWeight.Bold, color = VerdeTexto)
                        Text(
                            "${desde.format(DfUi)} – ${hasta.format(DfUi)}",
                            color = VerdeTexto
                        )
                        Text("Máximo 6 meses", color = VerdeTexto, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SelectorTipoExport(
                tipos = contexto?.tipos.orEmpty(),
                seleccionado = tipoId,
                onChange = onTipo
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = incluirAnulados, onCheckedChange = onAnulados, enabled = !generando)
                Text("Incluir anulados", modifier = Modifier.clickable(enabled = !generando) {
                    onAnulados(!incluirAnulados)
                })
            }

            if (error != null) {
                Text(error, color = Color(0xFFC62828))
            }
            if (generando) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.primary)
                Text("Generando PDF…")
            }

            Button(
                onClick = onGenerar,
                enabled = !generando && periodoValido,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generar PDF", fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun CampoFechaExport(
    etiqueta: String,
    valor: LocalDate?,
    habilitado: Boolean,
    min: LocalDate?,
    max: LocalDate?,
    tituloDialogo: String,
    onElegir: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        OutlinedTextField(
            value = valor?.format(DfUi).orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = habilitado,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = habilitado) {
                    abrirCalendario(context, tituloDialogo, valor, min, max, onElegir)
                },
            placeholder = { Text("Seleccionar fecha") },
            trailingIcon = {
                IconButton(
                    onClick = { abrirCalendario(context, tituloDialogo, valor, min, max, onElegir) },
                    enabled = habilitado
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = "Abrir calendario")
                }
            }
        )
        if (!habilitado) {
            Text(
                "Selecciona primero la fecha Desde",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

private fun abrirCalendario(
    context: android.content.Context,
    titulo: String,
    actual: LocalDate?,
    min: LocalDate?,
    max: LocalDate?,
    onElegir: (LocalDate) -> Unit
) {
    val base = actual ?: max ?: hoyLima()
    val dialog = DatePickerDialog(
        context,
        { _, y, m, d -> onElegir(LocalDate.of(y, m + 1, d)) },
        base.year,
        base.monthValue - 1,
        base.dayOfMonth
    )
    dialog.setTitle(titulo)
    min?.let {
        dialog.datePicker.minDate = it.atStartOfDay(Lima).toInstant().toEpochMilli()
    }
    max?.let {
        dialog.datePicker.maxDate = it.atStartOfDay(Lima).toInstant().toEpochMilli()
    }
    dialog.show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorTipoExport(
    tipos: List<TipoExportDto>,
    seleccionado: Long?,
    onChange: (Long?) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    val etiqueta = when (seleccionado) {
        null -> "Todos los tipos"
        else -> tipos.firstOrNull { it.idTipo == seleccionado }?.nombre ?: "Todos los tipos"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tipo de mantenimiento", fontWeight = FontWeight.SemiBold)
        ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
            OutlinedTextField(
                value = etiqueta,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) }
            )
            ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                DropdownMenuItem(
                    text = { Text("Todos los tipos") },
                    onClick = { onChange(null); abierto = false }
                )
                tipos.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.nombre ?: t.codigo ?: "#${t.idTipo}") },
                        onClick = { onChange(t.idTipo); abierto = false }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VistaPreviaExport(
    archivo: File?,
    aviso: String?,
    onCerrarAviso: () -> Unit,
    onVolver: () -> Unit,
    onWhatsApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val paginas = remember(archivo?.absolutePath, archivo?.length()) {
        renderPdfPages(archivo)
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Vista previa del PDF", fontWeight = FontWeight.Bold, color = scheme.primary)
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface)
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VerdeOk,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("¡Listo!", fontWeight = FontWeight.Bold, color = VerdeTexto)
                        Text("Tu historial ha sido generado correctamente.", color = VerdeTexto)
                    }
                }
                if (paginas.isEmpty()) {
                    Text("No se pudo mostrar la vista previa del PDF.", color = Color(0xFFC62828))
                } else {
                    paginas.forEach { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Página PDF",
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onWhatsApp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
            ) { Text("Compartir por WhatsApp", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = onVolver,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Volver") }
        }
    }
    if (aviso != null) {
        AlertDialog(
            onDismissRequest = onCerrarAviso,
            title = { Text("WhatsApp") },
            text = { Text(aviso) },
            confirmButton = { TextButton(onClick = onCerrarAviso) { Text("Entendido") } }
        )
    }
}

private fun renderPdfPages(archivo: File?): List<Bitmap> {
    if (archivo == null || !archivo.exists()) return emptyList()
    return runCatching {
        ParcelFileDescriptor.open(archivo, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                buildList {
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val bmp = Bitmap.createBitmap(
                                page.width * 2,
                                page.height * 2,
                                Bitmap.Config.ARGB_8888
                            )
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            add(bmp)
                        }
                    }
                }
            }
        }
    }.getOrDefault(emptyList())
}
