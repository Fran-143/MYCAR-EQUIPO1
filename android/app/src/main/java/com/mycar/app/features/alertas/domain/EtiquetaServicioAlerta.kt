package com.mycar.app.features.alertas.domain

/**
 * Nombre único de servicio para tarjeta, detalle, notificación y FCM.
 * OTRO → servicio_otro; resto → nombre de catálogo (tipo).
 */
fun etiquetaServicioAlerta(
    codigoTipo: String,
    tipo: String,
    servicioOtro: String?
): String {
    val otro = servicioOtro?.trim()?.takeIf { it.isNotEmpty() }
    return if (codigoTipo.equals("OTRO", ignoreCase = true)) {
        otro ?: tipo.trim().ifBlank { "Otro servicio" }
    } else {
        tipo.trim().ifBlank { otro ?: "Servicio" }
    }
}
