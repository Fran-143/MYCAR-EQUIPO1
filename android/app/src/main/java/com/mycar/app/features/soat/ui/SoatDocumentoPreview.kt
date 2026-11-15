package com.mycar.app.features.soat.ui

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarTeal
import java.io.File

@Composable
fun SoatVistaDocumento(bytes: ByteArray?, esPdf: Boolean, cargando: Boolean, error: String?) {
    when {
        cargando -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
        error != null -> Text(error, color = androidx.compose.ui.graphics.Color(0xFFC62828))
        bytes == null -> Text("Sin vista previa.", color = MyCarMuted)
        esPdf -> PdfPreview(bytes)
        else -> {
            val bmp = remember(bytes) { runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Documento SOAT",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    contentScale = ContentScale.FillWidth
                )
            } else Text("No se pudo mostrar la imagen.", color = MyCarMuted)
        }
    }
}

@Composable
private fun PdfPreview(bytes: ByteArray) {
    val context = LocalContext.current
    val paginas = remember(bytes) {
        runCatching {
            val file = File.createTempFile("soat_preview", ".pdf", context.cacheDir)
            file.writeBytes(bytes)
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
    if (paginas.isEmpty()) {
        Text("No se pudo mostrar el PDF.", color = MyCarMuted)
        return
    }
    // Sin scroll propio: el scaffold padre ya scrollea en vertical.
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