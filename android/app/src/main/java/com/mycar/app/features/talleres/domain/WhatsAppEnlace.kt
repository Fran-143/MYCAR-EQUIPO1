package com.mycar.app.features.talleres.domain

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Enlace oficial https://wa.me/ para contacto voluntario (RF24).
 * Perú: normaliza a 51 + 9 dígitos. No adjunta PII del usuario/vehículo.
 */
object WhatsAppEnlace {
    const val MENSAJE_CONSULTA_DEFAULT =
        "Hola, quisiera consultar disponibilidad de un servicio."

    /** E.164 sin '+' para wa.me, o null si el número no es válido para PE. */
    fun normalizarPeruE164(telefono: String?): String? {
        if (telefono.isNullOrBlank()) return null
        val digitos = telefono.filter { it.isDigit() }
        return when {
            digitos.length == 9 && digitos.startsWith("9") -> "51$digitos"
            digitos.length == 11 && digitos.startsWith("51") && digitos[2] == '9' -> digitos
            digitos.length == 10 && digitos.startsWith("09") -> "51${digitos.drop(1)}"
            else -> null
        }
    }

    fun uriWhatsApp(telefono: String?, mensaje: String = MENSAJE_CONSULTA_DEFAULT): String? {
        val e164 = normalizarPeruE164(telefono) ?: return null
        val texto = mensaje.trim().ifEmpty { MENSAJE_CONSULTA_DEFAULT }
        // wa.me espera %20; URLEncoder usa '+' para espacios y a veces falla en apps.
        val encoded = URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20")
        return "https://wa.me/$e164?text=$encoded"
    }

    /** URI tel: con el número normalizado (o dígitos originales si no es PE válido). */
    fun uriLlamada(telefono: String?): String? {
        if (telefono.isNullOrBlank()) return null
        val e164 = normalizarPeruE164(telefono)
        val num = e164 ?: telefono.filter { it.isDigit() || it == '+' }
        if (num.isBlank()) return null
        return if (num.startsWith("+")) "tel:$num" else "tel:+$num"
    }

    /**
     * Abre WhatsApp sin depender de [Intent.resolveActivity] (en Android 11+ falla
     * sin `<queries>` y bloqueaba el botón aunque WhatsApp estuviera instalado).
     * @return null si OK; mensaje de error si no se pudo abrir.
     */
    fun abrir(context: Context, telefono: String?, mensaje: String = MENSAJE_CONSULTA_DEFAULT): String? {
        val wa = uriWhatsApp(telefono, mensaje) ?: return "Número de WhatsApp no válido para Perú."
        val uri = Uri.parse(wa)
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"),
            Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp.w4b"),
            Intent(Intent.ACTION_VIEW, uri)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return null
            } catch (_: ActivityNotFoundException) {
                // probar siguiente
            } catch (_: Exception) {
                // probar siguiente
            }
        }
        return "No se pudo abrir WhatsApp. Prueba llamar o instala WhatsApp."
    }
}
