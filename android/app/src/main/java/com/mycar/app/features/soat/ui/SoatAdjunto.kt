package com.mycar.app.features.soat.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mycar.app.features.soat.viewmodel.AdjuntoSoatUi
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import java.io.File
import java.text.DecimalFormat

@Composable
fun SoatSeccionAdjunto(
    adjunto: AdjuntoSoatUi?,
    error: String?,
    enabled: Boolean,
    onAdjunto: (AdjuntoSoatUi?) -> Unit,
    estiloPdf: Boolean = false
) {
    val context = LocalContext.current
    var uriFoto by remember { mutableStateOf<Uri?>(null) }
    val authority = "${context.packageName}.fileprovider"
    val tomarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && uriFoto != null) {
            val u = uriFoto!!
            metaAdjunto(context, u)?.let { onAdjunto(it) }
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
        uri?.let { metaAdjunto(context, it)?.let(onAdjunto) }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            metaAdjunto(context, it)?.let(onAdjunto)
        }
    }
    fun lanzarCamara() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val u = archivoCaptura(context)
            uriFoto = FileProvider.getUriForFile(context, authority, u)
            tomarFoto.launch(uriFoto)
        } else permisoCamara.launch(Manifest.permission.CAMERA)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!estiloPdf) {
            Text("Documento (opcional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MyCarNavy)
        }
        Text(
            if (estiloPdf) "JPG, PNG o PDF · máximo 10 MB (imagen hasta 5 MB)."
            else "Imagen JPG/PNG hasta 5 MB o PDF hasta 10 MB.",
            color = MyCarMuted,
            style = MaterialTheme.typography.bodySmall
        )
        if (estiloPdf) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SoatBotonSecundario("Tomar foto", { lanzarCamara() }, enabled)
                SoatBotonSecundario(
                    "Elegir de galería",
                    { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled
                )
                SoatBotonSecundario("Seleccionar PDF", { pdf.launch(arrayOf("application/pdf")) }, enabled)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { lanzarCamara() }, enabled = enabled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Cámara") }
                OutlinedButton(
                    onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Galería") }
                OutlinedButton(onClick = { pdf.launch(arrayOf("application/pdf")) }, enabled = enabled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("PDF") }
            }
        }
        adjunto?.let {
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE8F5E9)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(it.nombre, fontWeight = FontWeight.Medium, color = MyCarNavy)
                        Text(tamanoLegible(it.tamanoBytes), style = MaterialTheme.typography.bodySmall, color = MyCarMuted)
                    }
                    TextButton(onClick = { onAdjunto(null) }, enabled = enabled) { Text("Quitar") }
                }
            }
        }
        error?.let { Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall) }
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