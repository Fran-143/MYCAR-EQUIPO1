package com.mycar.app.features.talleres

import com.mycar.app.features.alertas.domain.ContactoTallerAlerta
import com.mycar.app.features.talleres.domain.PoliticaContactoTaller
import com.mycar.app.features.talleres.domain.WhatsAppEnlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Casos A/B/C con número controlado de prueba (solo unitario; no toca Neon/Render).
 * Teléfono de laboratorio del equipo: 956188469.
 */
class WhatsAppCasosABCTest {

    private val telEquipo = "956188469"

    private fun contacto(
        origen: String,
        autorizado: Boolean,
        tel: String? = telEquipo,
        wa: Boolean = true
    ) = ContactoTallerAlerta(
        idTaller = 99L,
        nombre = "Taller Prueba",
        telefono = tel,
        tieneWhatsapp = wa,
        contactoAutorizado = autorizado,
        origen = origen,
        origenSugerencia = "EXPLICITO",
        sinContactoDisponible = tel.isNullOrBlank()
    )

    @Test
    fun casoA_manual_bloqueaWhatsAppYLlamada() {
        val c = contacto(origen = "MANUAL", autorizado = false)
        assertFalse(PoliticaContactoTaller.puedeContactar(c))
        assertFalse(PoliticaContactoTaller.puedeWhatsApp(c))
        assertFalse(PoliticaContactoTaller.puedeLlamar(c))
        // El número existe en ficha manual, pero la app no debe abrir WA por política.
        assertNotNull(WhatsAppEnlace.uriWhatsApp(telEquipo))
    }

    @Test
    fun casoB_asociadoSinAutorizacion_bloquea() {
        val c = contacto(origen = "ASOCIADO", autorizado = false)
        assertFalse(PoliticaContactoTaller.puedeContactar(c))
        assertFalse(PoliticaContactoTaller.puedeWhatsApp(c))
        assertFalse(PoliticaContactoTaller.puedeLlamar(c))
    }

    @Test
    fun casoC_asociadoAutorizado_abreWaYLlamadaAlNumeroEquipo() {
        val c = contacto(origen = "ASOCIADO", autorizado = true)
        assertTrue(PoliticaContactoTaller.puedeContactar(c))
        assertTrue(PoliticaContactoTaller.puedeWhatsApp(c))
        assertTrue(PoliticaContactoTaller.puedeLlamar(c))
        val wa = WhatsAppEnlace.uriWhatsApp(c.telefono)
        assertNotNull(wa)
        assertTrue(wa!!.startsWith("https://wa.me/51956188469?text="))
        assertEquals("tel:+51956188469", WhatsAppEnlace.uriLlamada(c.telefono))
    }

    @Test
    fun casoC_asociadoAutorizadoSinWa_soloLlamada() {
        val c = contacto(origen = "ASOCIADO", autorizado = true, wa = false)
        assertTrue(PoliticaContactoTaller.puedeLlamar(c))
        assertFalse(PoliticaContactoTaller.puedeWhatsApp(c))
        assertNull(WhatsAppEnlace.uriWhatsApp(null))
    }
}
