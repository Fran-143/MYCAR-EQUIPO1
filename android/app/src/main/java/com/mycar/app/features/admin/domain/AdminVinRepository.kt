package com.mycar.app.features.admin.domain

import com.mycar.app.features.admin.data.AdminAccionResponse
import com.mycar.app.features.soporte.data.SolicitudDetalleDto
import com.mycar.app.features.soporte.data.SolicitudResumenDto

interface AdminVinRepository {
    suspend fun listar(estado: String?, q: String?): List<SolicitudResumenDto>
    suspend fun detalle(codigo: String): SolicitudDetalleDto
    suspend fun iniciarRevision(codigo: String): AdminAccionResponse
    suspend fun aprobar(codigo: String): AdminAccionResponse
    suspend fun rechazar(codigo: String, motivo: String): AdminAccionResponse
    suspend fun descargarEvidencia(codigo: String): ByteArray
}