package com.mycar.app.features.historial.export

import android.content.Context
import java.io.File

object ExportarHistorialPdfCache {
    private const val DIR = "export_historial_pdf"

    fun guardar(context: Context, bytes: ByteArray, nombreArchivo: String): File {
        val dir = File(context.cacheDir, DIR).also { it.mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val safe = nombreArchivo.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "Historial_MyCar.pdf" }
        val archivo = File(dir, safe)
        archivo.writeBytes(bytes)
        return archivo
    }

    fun limpiar(context: Context) {
        val dir = File(context.cacheDir, DIR)
        if (dir.exists()) {
            dir.listFiles()?.forEach { runCatching { it.delete() } }
        }
    }
}
