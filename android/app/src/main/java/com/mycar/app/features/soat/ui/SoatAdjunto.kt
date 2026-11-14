package com.mycar.app.features.soat.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mycar.app.features.soat.viewmodel.AdjuntoSoatUi
import com.mycar.app.ui.theme.MyCarBlue
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarOutlineSoft
import com.mycar.app.ui.theme.MyCarTile
import java.io.File
import java.text.DecimalFormat

private val AzulAdjunto = Color(0xFF1E4B9C)
private val FondoAdjunto = Color(0xFFF3F7FC)

@Composable
fun SoatSeccionAdjunto(
    adjunto: AdjuntoSoatUi?,
    error: String?,
    enabled: Boolean,
    onAdjunto: (AdjuntoSoatUi?) -> Unit,
    estiloPdf: Boolean = false,
    etiqueta: String = "Documento del SOAT (opcional)",
    subtituloVacio: String = "Foto, captura o PDF del SOAT"
) {
    val context = LocalContext.current
    var uriFoto by remember { mutableStateOf<Uri?>(null) }
    var elegirFuente by remember { mutableStateOf(false) }
    val authority = "${context.packageName}.fileprovider"

    val tomarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && uriFoto != null) {
            metaAdjunto(context, uriFoto!!)?.let(onAdjunto)
            elegirFuente = false
        }
    }
    val permisoCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) {
            val u = archivoCaptura(context)
            uriFoto = FileProvider.getUriForFile(context, authority, u)
            tomarFoto.launch(uriFoto)
        }
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            metaAdjunto(context, it)?.let(onAdjunto)
            elegirFuente = false
        }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            metaAdjunto(context, it)?.let(onAdjunto)
            elegirFuente = false
        }
    }
    fun lanzarCamara() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val u = archivoCaptura(context)
            uriFoto = FileProvider.getUriForFile(context, authority, u)
            tomarFoto.launch(uriFoto)
        } else permisoCamara.launch(Manifest.permission.CAMERA)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = AzulAdjunto, modifier = Modifier.size(18.dp))
            Text(etiqueta, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = AzulAdjunto)
        }
        Text(
            if (estiloPdf) "JPG, PNG o PDF · máximo 10 MB (imagen hasta 5 MB)."
            else "Imagen JPG/PNG hasta 5 MB o PDF hasta 10 MB.",
            color = MyCarMuted,
            style = MaterialTheme.typography.bodySmall
        )

        when {
            adjunto != null -> SoatAdjuntoPreview(
                adjunto = adjunto,
                enabled = enabled,
                onQuitar = { onAdjunto(null) }
            )
            elegirFuente -> SoatSelectorFuente(
                enabled = enabled,
                onCamara = { lanzarCamara() },
                onGaleria = {
                    galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onPdf = { pdf.launch(arrayOf("application/pdf")) }
            )
            else -> SoatZonaAdjuntar(
                enabled = enabled,
                subtitulo = subtituloVacio,
                onClick = { if (enabled) elegirFuente = true }
            )
        }
        error?.let { Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SoatZonaAdjuntar(
    enabled: Boolean,
    subtitulo: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 148.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MyCarOutlineSoft, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = FondoAdjunto,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, MyCarOutlineSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = AzulAdjunto, modifier = Modifier.size(30.dp))
            }
            Text("Adjuntar documento", fontWeight = FontWeight.Bold, color = AzulAdjunto)
            Text(subtitulo, color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SoatSelectorFuente(
    enabled: Boolean,
    onCamara: () -> Unit,
    onGaleria: () -> Unit,
    onPdf: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Elige la fuente del documento", color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoatFuenteCard(
                modifier = Modifier.weight(1f),
                icono = Icons.Filled.PhotoCamera,
                titulo = "Cámara",
                detalle = "Tomar foto del documento",
                enabled = enabled,
                onClick = onCamara
            )
            SoatFuenteCard(
                modifier = Modifier.weight(1f),
                icono = Icons.Filled.Image,
                titulo = "Galería",
                detalle = "Seleccionar desde tu galería",
                enabled = enabled,
                onClick = onGaleria
            )
        }
        SoatFuenteCard(
            modifier = Modifier.fillMaxWidth(),
            icono = Icons.Filled.Description,
            titulo = "PDF",
            detalle = "Seleccionar un archivo PDF",
            enabled = enabled,
            onClick = onPdf
        )
    }
}

@Composable
private fun SoatFuenteCard(
    modifier: Modifier,
    icono: ImageVector,
    titulo: String,
    detalle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = MyCarTile,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MyCarOutlineSoft)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icono, contentDescription = null, tint = AzulAdjunto, modifier = Modifier.size(26.dp))
            Text(titulo, fontWeight = FontWeight.Bold, color = AzulAdjunto)
            Text(detalle, color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SoatAdjuntoPreview(
    adjunto: AdjuntoSoatUi,
    enabled: Boolean,
    onQuitar: () -> Unit
) {
    val context = LocalContext.current
    val esImagen = adjunto.contentType.startsWith("image/")
    val bitmap = remember(adjunto.uri, adjunto.contentType) {
        if (!esImagen) {
            null
        } else {
            runCatching {
                context.contentResolver.openInputStream(adjunto.uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MyCarOutlineSoft, RoundedCornerShape(16.dp)),
        color = Color.White,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(FondoAdjunto)
                    .border(1.dp, MyCarOutlineSoft, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Vista previa",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (esImagen) Icons.Filled.Image else Icons.Filled.Description,
                        contentDescription = null,
                        tint = if (esImagen) AzulAdjunto else Color(0xFFC62828),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    adjunto.nombre,
                    fontWeight = FontWeight.SemiBold,
                    color = MyCarBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(tamanoLegible(adjunto.tamanoBytes), color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onQuitar, enabled = enabled) {
                Icon(Icons.Filled.Close, contentDescription = "Quitar documento", tint = MyCarMuted)
            }
        }
    }
}

private fun archivoCaptura(context: Context): File {
    val dir = File(context.cacheDir, "soat_capture").apply { mkdirs() }
    return File(dir, "capture_${System.currentTimeMillis()}.jpg")
}

private fun metaAdjunto(context: Context, uri: Uri): AdjuntoSoatUi? {
    val cr = context.contentResolver
    val type = cr.getType(uri) ?: return null
    val nombre = cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else "documento"
    } ?: "documento"
    val tamano = cr.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    return AdjuntoSoatUi(uri, nombre, type, tamano)
}

private fun tamanoLegible(bytes: Long): String {
    val df = DecimalFormat("#.##")
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${df.format(bytes / 1024.0)} KB"
        else -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
    }
}

suspend fun leerUriSoat(context: Context, uri: Uri): ByteArray =
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("No se pudo leer el archivo.")
