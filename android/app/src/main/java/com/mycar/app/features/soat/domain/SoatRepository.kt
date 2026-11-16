package com.mycar.app.features.soat.domain

import com.mycar.app.features.soat.data.*

interface SoatRepository {
    suspend fun consultar(idVehiculo: Long): SoatConsultaResponse
    suspend fun registrar(idVehiculo: Long, solicitud: SoatMutacionDto): SoatDto
    suspend fun corregir(idVehiculo: Long, idSoat: Long, solicitud: SoatMutacionDto): SoatDto
    suspend fun renovar(idVehiculo: Long, solicitud: SoatMutacionDto): SoatDto
    suspend fun recordatorio(idVehiculo: Long, idSoat: Long, activo: Boolean): SoatDto
    /** Sube adjunto: multipart si el servidor lo soporta; si no, presign (Render actual). */
    suspend fun subirDocumento(
        idVehiculo: Long,
        bytes: ByteArray,
        contentType: String,
        nombreOriginal: String
    ): SoatDocumentoRefDto
    suspend fun descargarDocumento(idVehiculo: Long, idSoat: Long): ByteArray
}
