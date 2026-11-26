package com.mycar.app.features.talleres.domain

import com.mycar.app.features.alertas.domain.ContactoTallerAlerta

/**
 * RF24 / V26: WhatsApp y llamada solo para taller ASOCIADO activo con
 * [ContactoTallerAlerta.contactoAutorizado] explícito y datos válidos.
 * MANUAL o ASOCIADO sin autorización no habilitan acciones de contacto.
 */
object PoliticaContactoTaller {

    fun puedeContactar(contacto: ContactoTallerAlerta?): Boolean {
        if (contacto == null) return false
        if (contacto.idTaller == null || contacto.idTaller <= 0) return false
        if (!contacto.origen.equals("ASOCIADO", ignoreCase = true)) return false
        if (!contacto.contactoAutorizado) return false
        if (contacto.sinContactoDisponible) return false
        if (contacto.telefono.isNullOrBlank()) return false
        return true
    }

    fun puedeWhatsApp(contacto: ContactoTallerAlerta?): Boolean {
        if (!puedeContactar(contacto)) return false
        if (contacto!!.tieneWhatsapp != true) return false
        return WhatsAppEnlace.normalizarPeruE164(contacto.telefono) != null
    }

    fun puedeLlamar(contacto: ContactoTallerAlerta?): Boolean {
        if (!puedeContactar(contacto)) return false
        return WhatsAppEnlace.uriLlamada(contacto!!.telefono) != null
    }
}
