package com.mycar.app.features.talleres

import com.mycar.app.features.alertas.domain.ContactoTallerAlerta
import com.mycar.app.features.talleres.domain.PoliticaContactoTaller
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliticaContactoTallerTest {

    private fun contacto(
        origen: String = "ASOCIADO",
        autorizado: Boolean = true,
        tel: String? = "987654321",
        wa: Boolean = true,
        sin: Boolean = false
    ) = ContactoTallerAlerta(
        idTaller = 10L,
        nombre = "Taller",
        telefono = tel,
        tieneWhatsapp = wa,
        contactoAutorizado = autorizado,
        origen = origen,
        origenSugerencia = "EXPLICITO",
        sinContactoDisponible = sin
    )

    @Test fun asociadoAutorizadoPermiteWaYLlamada() {
        val c = contacto()
        assertTrue(PoliticaContactoTaller.puedeContactar(c))
        assertTrue(PoliticaContactoTaller.puedeWhatsApp(c))
        assertTrue(PoliticaContactoTaller.puedeLlamar(c))
    }

    @Test fun manualNuncaContactaAunqueTengaTelefonoYWa() {
        val c = contacto(origen = "MANUAL", autorizado = true, wa = true)
        assertFalse(PoliticaContactoTaller.puedeContactar(c))
        assertFalse(PoliticaContactoTaller.puedeWhatsApp(c))
        assertFalse(PoliticaContactoTaller.puedeLlamar(c))
    }

    @Test fun asociadoSinAutorizacionBloquea() {
        val c = contacto(autorizado = false)
        assertFalse(PoliticaContactoTaller.puedeContactar(c))
        assertFalse(PoliticaContactoTaller.puedeWhatsApp(c))
        assertFalse(PoliticaContactoTaller.puedeLlamar(c))
    }

    @Test fun asociadoAutorizadoSinTelefonoBloquea() {
        val c = contacto(tel = null, sin = true)
        assertFalse(PoliticaContactoTaller.puedeContactar(c))
    }

    @Test fun asociadoAutorizadoSinWaSoloLlamadaSiHayTel() {
        val c = contacto(wa = false, tel = "987654321")
        assertTrue(PoliticaContactoTaller.puedeContactar(c))
        assertFalse(PoliticaContactoTaller.puedeWhatsApp(c))
        assertTrue(PoliticaContactoTaller.puedeLlamar(c))
    }
}
