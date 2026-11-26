package com.mycar.app.features.soat.ui

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarTeal
import java.io.File

/**
 * Vista previa con capa de privacidad. PDF e imagen requieren revelación explícita.
 * [onRevelar] / [onOcultar] permiten diferir la descarga hasta el tap.
 */
@Composable
fun SoatVistaDocumento(
    bytes: ByteArray?,
    esPdf: Boolean,
    cargando: Boolean,
    error: String?,
    revelado: Boolean,
    onRevelar: () -> Unit,
    onOcultar: () -> Unit,
    tieneDocumento: Boolean = true
) {
    if (!tieneDocumento) {
        Text("Sin vista previa.", color = MyCarMuted)
        return
    }
    when {
        !revelado -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color(0xFFE8EEF6), RoundedCornerShape(12.dp))
                    .clickable(onClick = onRevelar),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (esPdf || bytes == null) "Mostrar documento" else "Toca para revelar",
                        fontWeight = FontWeight.SemiBold,
                        color = MyCarNavy
                    )
                    Text("Protegido por privacidad", color = MyCarMuted)
                }
            }
        }
        cargando -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
        error != null -> Text(error, color = Color(0xFFC62828))
        bytes == null -> Text("Sin vista previa.", color = MyCarMuted)
        esPdf -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Ocultar documento",
                    color = MyCarNavy,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onOcultar)
                )
                PdfPreview(bytes)
            }
        }
        else -> {
            val bmp = remember(bytes) {
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            }
            if (bmp != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOcultar)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Documento SOAT",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        contentScale = ContentScale.FillWidth
                    )
                    Text(
                        "Toca para ocultar",
                        color = MyCarMuted,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            } else Text("No se pudo mostrar la imagen.", color = MyCarMuted)
        }
    }
}

@Composable
private fun PdfPreview(bytes: ByteArray) {
    val context = LocalContext.current
    var tempFile by remember(bytes) { mutableStateOf<File?>(null) }
    val paginas = remember(bytes) {
        runCatching {
            val file = File.createTempFile("soat_preview", ".pdf", context.cacheDir)
            file.writeBytes(bytes)
            tempFile = file
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                (0 until renderer.pageCount.coerceAtMost(3)).map { idx ->
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
        Text("No se pudo mostrar el PDF.", color = MyCarMuted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paginas.forEach { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}
