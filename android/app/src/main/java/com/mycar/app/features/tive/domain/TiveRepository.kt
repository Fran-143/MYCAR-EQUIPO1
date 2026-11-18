package com.mycar.app.features.tive.domain

import com.mycar.app.features.tive.data.*

interface TiveRepository {
    suspend fun precheck(idVehiculo: Long): TivePrecheckResponse
    suspend fun consultar(idVehiculo: Long): TiveConsultaResponse
    suspend fun registrar(idVehiculo: Long, solicitud: TiveRegistrarRequest): TiveDto
    suspend fun actualizar(idVehiculo: Long, idTive: Long, solicitud: TiveActualizarRequest): TiveDto
    suspend fun subirDocumento(
        idVehiculo: Long,
        bytes: ByteArray,
        contentType: String,
        nombreOriginal: String
    ): TiveDocumentoRefDto
    suspend fun descargarDocumento(idVehiculo: Long, idTive: Long): ByteArray
}