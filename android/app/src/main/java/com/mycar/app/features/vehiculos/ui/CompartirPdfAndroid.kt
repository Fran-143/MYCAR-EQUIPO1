package com.mycar.app.features.vehiculos.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object CompartirPdfAndroid {
    private const val DIR = "compartir_pdf"
    private const val NOMBRE = "MyCar-vehiculo.pdf"

    fun guardarTemporal(context: Context, bytes: ByteArray): File {
        val dir = File(context.cacheDir, DIR).also { it.mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val archivo = File(dir, NOMBRE)
        archivo.writeBytes(bytes)
        return archivo
    }

    fun limpiarTemporales(context: Context) {
        val dir = File(context.cacheDir, DIR)
        if (dir.exists()) {
            dir.listFiles()?.forEach { runCatching { it.delete() } }
        }
    }

    fun uriParaCompartir(context: Context, archivo: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)

    fun mensajeWhatsApp(marca: String?, modelo: String?): String {
        val nombre = listOfNotNull(
            marca?.takeIf { it.isNotBlank() },
            modelo?.takeIf { it.isNotBlank() }
        ).joinToString(" ").ifBlank { "vehículo" }
        return "Mira mi $nombre en MyCar \uD83D\uDE97. Te comparto una ficha con información general de mi vehículo"
    }

    /** Copia el mensaje al portapapeles. @return true si se copió. */
    fun copiarMensaje(context: Context, marca: String?, modelo: String?): Boolean {
        return runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("MyCar", mensajeWhatsApp(marca, modelo)))
            true
        }.getOrDefault(false)
    }

    /**
     * Comparte PDF por WhatsApp: copia el mensaje al portapapeles y lanza
     * ACTION_SEND con EXTRA_STREAM (PDF) + EXTRA_TEXT.
     * WhatsApp a menudo omite EXTRA_TEXT con un adjunto PDF; el portapapeles
     * permite pegar el comentario del documento.
     * @return null si se lanzó el intent; mensaje de error si no hay app.
     */
    fun compartirWhatsApp(
        context: Context,
        archivo: File,
        marca: String? = null,
        modelo: String? = null
    ): String? {
        val uri = uriParaCompartir(context, archivo)
        val mensaje = mensajeWhatsApp(marca, modelo)
        copiarMensaje(context, marca, modelo)
        val paquetes = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in paquetes) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, mensaje)
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
        return compartirConChooser(context, archivo, mensaje, "Compartir PDF con…")
    }

    fun compartirCorreo(context: Context, archivo: File): String? {
        val uri = uriParaCompartir(context, archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, "Información pública del vehículo — MyCar")
            putExtra(
                Intent.EXTRA_TEXT,
                "Adjunto una ficha informativa generada por MyCar. No sustituye la documentación oficial."
            )
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(Intent.createChooser(intent, "Enviar por correo"))
                null
            } else {
                compartirConChooser(context, archivo, null, "Compartir PDF con…")
            }
        } catch (_: ActivityNotFoundException) {
            compartirConChooser(context, archivo, null, "Compartir PDF con…")
        }
    }

    private fun compartirConChooser(
        context: Context,
        archivo: File,
        mensaje: String?,
        titulo: String
    ): String? {
        val uri = uriParaCompartir(context, archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!mensaje.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, mensaje)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            if (intent.resolveActivity(context.packageManager) == null) {
                "No hay una aplicación compatible instalada. Puedes cancelar o instalar WhatsApp/correo."
            } else {
                context.startActivity(Intent.createChooser(intent, titulo))
                null
            }
        } catch (_: ActivityNotFoundException) {
            "No hay una aplicación compatible instalada. Puedes cancelar o instalar WhatsApp/correo."
        }
    }
}
