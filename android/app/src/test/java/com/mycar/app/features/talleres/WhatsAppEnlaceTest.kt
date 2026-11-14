package com.mycar.app.features.talleres

import com.mycar.app.features.talleres.domain.WhatsAppEnlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppEnlaceTest {
    @Test fun normalizaNueveDigitosPeru() {
        assertEquals("51987654321", WhatsAppEnlace.normalizarPeruE164("987654321"))
        assertEquals("51987654321", WhatsAppEnlace.normalizarPeruE164("987 654 321"))
        assertEquals("51987654321", WhatsAppEnlace.normalizarPeruE164("+51 987 654 321"))
        assertEquals("51987654321", WhatsAppEnlace.normalizarPeruE164("0987654321"))
    }

    @Test fun rechazaNumerosInvalidos() {
        assertNull(WhatsAppEnlace.normalizarPeruE164(null))
        assertNull(WhatsAppEnlace.normalizarPeruE164(""))
        assertNull(WhatsAppEnlace.normalizarPeruE164("12345"))
        assertNull(WhatsAppEnlace.normalizarPeruE164("5198765432")) // 10 digitos incompletos
    }

    @Test fun construyeWaMeConMensajeSinPii() {
        val uri = WhatsAppEnlace.uriWhatsApp("987654321")!!
        assertTrue(uri.startsWith("https://wa.me/51987654321?text="))
        assertTrue(uri.contains("consultar"))
        assertTrue(!uri.contains("placa", ignoreCase = true))
        assertTrue(!uri.contains("ABC", ignoreCase = true))
    }

    @Test fun uriLlamadaUsaE164() {
        assertEquals("tel:+51987654321", WhatsAppEnlace.uriLlamada("987654321"))
    }
}
