package com.mycar.app.features.fotos.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

private const val MAX_BYTES = 5L * 1024L * 1024L
private val AzulFoto = Color(0xFF163468)
private val CelesteFoto = Color(0xFF079FDC)
private val FondoOpcion = Color(0xFFF0F7FC)
private val BordeSuave = Color(0xFFD7E3EF)

data class FotoListaParaSubir(
    val bytes: ByteArray,
    val contentType: String,
    val nombre: String
)

/**
 * Flujo cámara/galería (JPG/PNG ≤5 MiB) con vista previa Confirmar/Cancelar.
 * Sin PDF ni crop library (preview circular/cuadrado según [circular]).
 */
@Composable
fun FlujoSelectorFotoPersonalizada(
    activo: Boolean,
    circular: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmada: (FotoListaParaSubir) -> Unit,
    onError: (String) -> Unit = {}
) {
    if (!activo) return
    val context = LocalContext.current
    var uriCaptura by remember { mutableStateOf<Uri?>(null) }
    var previa by remember { mutableStateOf<FotoListaParaSubir?>(null) }
    var elegirFuente by remember { mutableStateOf(true) }
    val authority = "${context.packageName}.fileprovider"

    fun limpiarTemporales() {
        runCatching {
            File(context.cacheDir, "fotos_capture").listFiles()?.forEach { it.delete() }
            File(context.cacheDir, "fotos_adjuntos").listFiles()?.forEach { it.delete() }
        }
    }

    fun cerrarSinExito() {
        previa = null
        elegirFuente = true
        limpiarTemporales()
        onDismiss()
    }

    fun aceptarUri(uri: Uri) {
        val meta = metaFoto(context, uri)
        if (meta == null) {
            onError("Archivo no válido. Usa JPG o PNG de hasta 5 MB.")
            limpiarTemporales()
            return
        }
        previa = meta
        elegirFuente = false
    }

    val tomarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && uriCaptura != null) aceptarUri(uriCaptura!!)
        else if (!ok) {
            elegirFuente = true
        }
    }
    val permisoCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) {
            val u = archivoCaptura(context)
            uriCaptura = FileProvider.getUriForFile(context, authority, u)
            tomarFoto.launch(uriCaptura)
        } else {
            onError("Se necesita permiso de cámara para tomar la foto.")
            cerrarSinExito()
        }
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) aceptarUri(uri)
        else elegirFuente = true
    }

    fun lanzarCamara() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val u = archivoCaptura(context)
            uriCaptura = FileProvider.getUriForFile(context, authority, u)
            tomarFoto.launch(uriCaptura)
        } else permisoCamara.launch(Manifest.permission.CAMERA)
    }

    val candidata = previa
    if (candidata != null) {
        val bitmap = remember(candidata.bytes) {
            BitmapFactory.decodeByteArray(candidata.bytes, 0, candidata.bytes.size)
        }
        AlertDialog(
            onDismissRequest = { cerrarSinExito() },
            icon = {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CelesteFoto.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = CelesteFoto)
                }
            },
            title = {
                Text(
                    "Vista previa",
                    fontWeight = FontWeight.Bold,
                    color = AzulFoto,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Vista previa",
                            modifier = Modifier
                                .then(
                                    if (circular) Modifier.size(168.dp).clip(CircleShape)
                                    else Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                ),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        "¿Usar esta imagen?",
                        Modifier.padding(top = 14.dp),
                        color = Color(0xFF6881A5),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val foto = candidata
                        previa = null
                        elegirFuente = true
                        onConfirmada(foto)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AzulFoto)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Confirmar")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { cerrarSinExito() },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BordeSuave),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AzulFoto)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Cancelar")
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(22.dp)
        )
    } else if (elegirFuente) {
        AlertDialog(
            onDismissRequest = { cerrarSinExito() },
            icon = {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AzulFoto.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = AzulFoto, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text(
                    "Seleccionar imagen",
                    fontWeight = FontWeight.Bold,
                    color = AzulFoto,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "JPG o PNG · máximo 5 MB",
                        color = Color(0xFF6881A5),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OpcionFuenteFoto(
                            modifier = Modifier.weight(1f),
                            icono = Icons.Filled.PhotoCamera,
                            titulo = "Cámara",
                            detalle = "Tomar foto",
                            onClick = { lanzarCamara() }
                        )
                        OpcionFuenteFoto(
                            modifier = Modifier.weight(1f),
                            icono = Icons.Filled.Image,
                            titulo = "Galería",
                            detalle = "Elegir archivo",
                            onClick = {
                                galeria.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { cerrarSinExito() }) {
                    Text("Cancelar", color = Color(0xFF6881A5))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(22.dp)
        )
    }
}

@Composable
private fun OpcionFuenteFoto(
    modifier: Modifier,
    icono: ImageVector,
    titulo: String,
    detalle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = FondoOpcion,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BordeSuave)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(icono, contentDescription = null, tint = CelesteFoto, modifier = Modifier.size(24.dp))
            }
            Text(titulo, fontWeight = FontWeight.SemiBold, color = AzulFoto)
            Text(
                detalle,
                color = Color(0xFF6881A5),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun archivoCaptura(context: Context): File {
    val dir = File(context.cacheDir, "fotos_capture").apply { mkdirs() }
    return File(dir, "capture_${System.currentTimeMillis()}.jpg")
}

private fun metaFoto(context: Context, uri: Uri): FotoListaParaSubir? {
    val cr = context.contentResolver
    val nombre = cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else "foto" }
        ?: "foto"
    var type = cr.getType(uri)
    if (type.isNullOrBlank()) {
        type = when {
            nombre.endsWith(".png", ignoreCase = true) -> "image/png"
            nombre.endsWith(".jpg", ignoreCase = true) ||
                nombre.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            else -> null
        }
    }
    if (type != "image/jpeg" && type != "image/png" && type != "image/jpg") return null
    if (type == "image/jpg") type = "image/jpeg"
    val bytes = runCatching {
        cr.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return null
    if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
    val esJpeg = bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
    val esPng = bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()
    if (!esJpeg && !esPng) return null
    val ct = if (esPng) "image/png" else "image/jpeg"
    runCatching {
        val dir = File(context.cacheDir, "fotos_adjuntos").apply { mkdirs() }
        val ext = if (ct.contains("png")) ".png" else ".jpg"
        File(dir, "adj_${System.currentTimeMillis()}$ext").writeBytes(bytes)
    }
    return FotoListaParaSubir(bytes = bytes, contentType = ct, nombre = nombre)
}
