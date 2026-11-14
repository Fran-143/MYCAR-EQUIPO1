package com.mycar.app.features.mantenimiento.domain

/**
 * RF24: abrir el mismo formulario RF17 en modo DESDE_ALERTA.
 * Vehículo, tipo y servicio OTRO vienen precargados y bloqueados.
 */
data class ContextoDesdeAlerta(
    val idAlerta: Long,
    val idVehiculo: Long,
    val codigoTipo: String,
    val servicioOtro: String? = null
)
