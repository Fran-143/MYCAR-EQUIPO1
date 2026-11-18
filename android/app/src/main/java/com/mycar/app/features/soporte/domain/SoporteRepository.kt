package com.mycar.app.features.soporte.domain

import com.mycar.app.features.soporte.data.*

interface SoporteRepository {
    suspend fun vehiculosElegibles(): List<VehiculoElegibleDto>
    suspend fun listarSolicitudes(): List<SolicitudResumenDto>
    suspend fun detalle(codigo: String): SolicitudDetalleDto
    suspend fun crearCorreccionVin(
        idOperacion: String,
        idVehiculo: Long,
        vinPropuesto: String,
        motivo: String,
        bytes: ByteArray?,
        contentType: String?,
        nombreOriginal: String?
    ): CrearSolicitudResponse
    suspend fun descargarEvidencia(codigo: String): ByteArray
}