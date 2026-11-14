package com.mycar.app.features.talleres.domain

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
        val encoded = URLEncoder.encode(texto, StandardCharsets.UTF_8)
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
}
