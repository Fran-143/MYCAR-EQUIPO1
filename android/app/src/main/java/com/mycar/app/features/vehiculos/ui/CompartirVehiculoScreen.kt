package com.mycar.app.features.vehiculos.ui

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycar.app.features.vehiculos.domain.CompartirVehiculoRepository
import com.mycar.app.features.vehiculos.domain.FalloCompartirVehiculo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val AzulCompartir = Color(0xFF003366)
private val VerdeWhatsApp = Color(0xFF25D366)
private val FondoClaro = Color(0xFFF5F7FA)

@Composable
fun CompartirVehiculoRoute(
    idVehiculo: Long,
    repository: CompartirVehiculoRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    modifier: Modifier = Modifier,
    marca: String? = null,
    modelo: String? = null
) {
    var etapa by remember(idVehiculo) { mutableStateOf("config") }
    var incluirFoto by remember(idVehiculo) { mutableStateOf(false) }
    var pdfBytes by remember(idVehiculo) { mutableStateOf<ByteArray?>(null) }
    var archivoTemporal by remember(idVehiculo) { mutableStateOf<File?>(null) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var avisoCanal by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun limpiarYVolver() {
        archivoTemporal = null
        pdfBytes = null
        CompartirPdfAndroid.limpiarTemporales(context)
        onVolver()
    }

    fun volverAConfig() {
        archivoTemporal = null
        pdfBytes = null
        error = null
        etapa = "config"
    }

    BackHandler {
        when (etapa) {
            "preview" -> volverAConfig()
            else -> limpiarYVolver()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CompartirPdfAndroid.limpiarTemporales(context)
        }
    }

    when (etapa) {
        "config" -> CompartirConfigScreen(
            incluirFoto = incluirFoto,
            onIncluirFoto = { incluirFoto = it },
            cargando = cargando,
            error = error,
            onCancelar = { limpiarYVolver() },
            onContinuar = {
                cargando = true
                error = null
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            repository.descargarPdf(idVehiculo, incluirFoto)
                        }
                        val file = withContext(Dispatchers.IO) {
                            CompartirPdfAndroid.guardarTemporal(context, bytes)
                        }
                        pdfBytes = bytes
                        archivoTemporal = file
                        etapa = "preview"
                    } catch (f: FalloCompartirVehiculo) {
                        if (f.estadoHttp == 401) onIniciarSesion()
                        else error = f.message
                    } catch (_: Exception) {
                        error = "No se pudo generar el documento. Inténtalo nuevamente."
                    } finally {
                        cargando = false
                    }
                }
            },
            modifier = modifier
        )
        else -> CompartirPreviewScreen(
            pdfBytes = pdfBytes,
            marca = marca,
            modelo = modelo,
            cargando = cargando,
            error = error,
            avisoCanal = avisoCanal,
            onCerrarAviso = { avisoCanal = null },
            onVolverConfig = { volverAConfig() },
            onReintentar = {
                cargando = true
                error = null
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            repository.descargarPdf(idVehiculo, incluirFoto)
                        }
                        val file = withContext(Dispatchers.IO) {
                            CompartirPdfAndroid.guardarTemporal(context, bytes)
                        }
                        pdfBytes = bytes
                        archivoTemporal = file
                    } catch (f: FalloCompartirVehiculo) {
                        if (f.estadoHttp == 401) onIniciarSesion()
                        else error = f.message
                        pdfBytes = null
                        archivoTemporal = null
                    } catch (_: Exception) {
                        error = "No se pudo generar el documento. Inténtalo nuevamente."
                        pdfBytes = null
                        archivoTemporal = null
                    } finally {
                        cargando = false
                    }
                }
            },
            onWhatsApp = {
                val file = archivoTemporal
                if (file == null || !file.exists() || pdfBytes == null) {
                    avisoCanal = "El documento no está listo. Reintenta generar el PDF."
                } else {
                    val errorWa = CompartirPdfAndroid.compartirWhatsApp(context, file, marca, modelo)
                    avisoCanal = errorWa ?: (
                        "Se abrió WhatsApp con el PDF. El mensaje quedó en el portapapeles: " +
                            "si no aparece como comentario del documento, pégalo antes de enviar."
                        )
                }
            },
            onCorreo = {
                val file = archivoTemporal
                if (file == null || !file.exists() || pdfBytes == null) {
                    avisoCanal = "El documento no está listo. Reintenta generar el PDF."
                } else {
                    avisoCanal = CompartirPdfAndroid.compartirCorreo(context, file)
                }
            },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompartirConfigScreen(
    incluirFoto: Boolean,
    onIncluirFoto: (Boolean) -> Unit,
    cargando: Boolean,
    error: String?,
    onCancelar: () -> Unit,
    onContinuar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FondoClaro,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Compartir vehículo", fontWeight = FontWeight.Bold, color = AzulCompartir)
                        Text(
                            "Configura la ficha pública",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancelar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Información que se incluirá",
                        fontWeight = FontWeight.SemiBold,
                        color = AzulCompartir
                    )
                    Text("Marca, modelo, año de fabricación y tipo de combustible.")
                    Text(
                        "No se compartirá placa, VIN, kilometraje ni datos personales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Incluir fotografía del vehículo", fontWeight = FontWeight.Medium)
                            Text(
                                "Desactivada por defecto. Si la activas y no hay foto personalizada, se usará la ilustración de MyCar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = incluirFoto,
                            onCheckedChange = onIncluirFoto,
                            enabled = !cargando,
                            colors = SwitchDefaults.colors(checkedTrackColor = AzulCompartir)
                        )
                    }
                }
            }
            if (error != null) {
                Text(error, color = Color(0xFFC62828))
            }
            if (cargando) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = AzulCompartir)
                Text("Generando documento…")
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onContinuar,
                enabled = !cargando,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AzulCompartir)
            ) { Text("Continuar", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = onCancelar,
                enabled = !cargando,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Cancelar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompartirPreviewScreen(
    pdfBytes: ByteArray?,
    marca: String?,
    modelo: String?,
    cargando: Boolean,
    error: String?,
    avisoCanal: String?,
    onCerrarAviso: () -> Unit,
    onVolverConfig: () -> Unit,
    onReintentar: () -> Unit,
    onWhatsApp: () -> Unit,
    onCorreo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mensajeWa = remember(marca, modelo) { CompartirPdfAndroid.mensajeWhatsApp(marca, modelo) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FondoClaro,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vista previa del PDF", fontWeight = FontWeight.Bold, color = AzulCompartir)
                        Text(
                            "Así se verá la información al compartir",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolverConfig) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    cargando -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = AzulCompartir)
                        Text("Preparando vista previa…")
                    }
                    error != null -> {
                        Text(error, color = Color(0xFFC62828))
                        OutlinedButton(onClick = onReintentar) { Text("Reintentar") }
                    }
                    pdfBytes != null -> {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("MyCar", fontWeight = FontWeight.Bold, color = AzulCompartir)
                                Text(
                                    "Información pública",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray
                                )
                                Spacer(Modifier.height(8.dp))
                                PdfPreviewCompartir(pdfBytes)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F1FA),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Documento informativo generado por MyCar. No sustituye la documentación oficial del vehículo.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = AzulCompartir
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val listo = pdfBytes != null && !cargando && error == null
            if (listo) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F1FA),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Mensaje que se preparará al compartir",
                            fontWeight = FontWeight.SemiBold,
                            color = AzulCompartir,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(mensajeWa, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Al compartir, el mensaje se copia al portapapeles. Si WhatsApp no lo muestra con el PDF, pégalo en el comentario del documento.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onWhatsApp,
                enabled = listo,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VerdeWhatsApp)
            ) {
                Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compartir por WhatsApp", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCorreo,
                enabled = listo,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AzulCompartir)
            ) {
                Icon(Icons.Filled.Email, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compartir por correo")
            }
            TextButton(onClick = onVolverConfig, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Cambiar selección de fotografía")
            }
        }
    }
    if (avisoCanal != null) {
        AlertDialog(
            onDismissRequest = onCerrarAviso,
            title = { Text("Compartir") },
            text = { Text(avisoCanal) },
            confirmButton = {
                TextButton(onClick = onCerrarAviso) { Text("Entendido") }
            }
        )
    }
}

@Composable
private fun PdfPreviewCompartir(bytes: ByteArray) {
    val context = LocalContext.current
    var tempFile by remember(bytes) { mutableStateOf<File?>(null) }
    val paginas = remember(bytes) {
        runCatching {
            val file = File.createTempFile("rf40_preview", ".pdf", context.cacheDir)
            file.writeBytes(bytes)
            tempFile = file
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                (0 until renderer.pageCount.coerceAtMost(2)).map { idx ->
                    renderer.openPage(idx).use { page ->
                        val bmp = android.graphics.Bitmap.createBitmap(
                            page.width.coerceAtLeast(1),
                            page.height.coerceAtLeast(1),
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
    DisposableEffect(bytes) {
        onDispose {
            tempFile?.delete()
            tempFile = null
        }
    }
    if (paginas.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color(0xFFE8EEF6), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("No se pudo mostrar el PDF.", color = Color.Gray)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paginas.forEach { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Vista previa del PDF",
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}
