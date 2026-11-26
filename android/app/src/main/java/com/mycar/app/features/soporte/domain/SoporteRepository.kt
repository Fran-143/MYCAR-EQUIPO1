package com.mycar.app.features.soporte.domain

import com.mycar.app.features.soporte.data.CrearSolicitudResponse
import com.mycar.app.features.soporte.data.ResumenSoporteDto
import com.mycar.app.features.soporte.data.SolicitudDetalleDto
import com.mycar.app.features.soporte.data.SolicitudResumenDto
import com.mycar.app.features.soporte.data.VehiculoElegibleDto

interface SoporteRepository {
    suspend fun vehiculosElegibles(): List<VehiculoElegibleDto>
    suspend fun listarSolicitudes(): List<SolicitudResumenDto>
    suspend fun resumen(): ResumenSoporteDto
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

    suspend fun crearIncidenciaGeneral(
        idOperacion: String,
        categoria: String,
        temaOtro: String?,
        asunto: String,
        descripcion: String,
        bytes: ByteArray?,
        contentType: String?,
        nombreOriginal: String?
    ): CrearSolicitudResponse

    suspend fun descargarEvidencia(codigo: String): ByteArray
}
