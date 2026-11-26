package com.mycar.app.core

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Contacto oficial de soporte para pantallas de cuenta / seguridad.
 * Centralizado para no repetir direcciones ni enlaces antiguos.
 */
object SoporteMyCar {
    const val CORREO = "mycargrupo1@gmail.com"
    const val ASUNTO = "Soporte MyCar — Consulta sobre mi cuenta"
    const val ASUNTO_RECUPERACION = "MyCar — Solicitud de recuperación de cuenta"
    const val CUERPO_RECUPERACION =
        "Hola, solicito asistencia para recuperar mi cuenta de MyCar, que se encuentra pendiente de eliminación."

    /** Abre el cliente de correo con mailto:; si no hay app, copia el correo. */
    fun abrirCorreo(
        context: Context,
        asunto: String = ASUNTO,
        cuerpo: String? = null
    ) {
        val builder = StringBuilder("mailto:").append(Uri.encode(CORREO))
            .append("?subject=").append(Uri.encode(asunto))
        if (!cuerpo.isNullOrBlank()) {
            builder.append("&body=").append(Uri.encode(cuerpo))
        }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(builder.toString())
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Contactar soporte MyCar"))
        } catch (_: ActivityNotFoundException) {
            copiarCorreo(context)
        }
    }

    fun abrirRecuperacionCuenta(context: Context) {
        abrirCorreo(context, ASUNTO_RECUPERACION, CUERPO_RECUPERACION)
    }

    fun copiarCorreo(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("soporte_mycar", CORREO))
        Toast.makeText(
            context,
            "No hay app de correo. Se copió $CORREO",
            Toast.LENGTH_LONG
        ).show()
    }
}
