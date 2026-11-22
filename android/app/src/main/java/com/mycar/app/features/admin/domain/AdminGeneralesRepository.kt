package com.mycar.app.features.admin.domain

import com.mycar.app.features.admin.data.AdminAccionResponse
import com.mycar.app.features.soporte.data.ContadoresIncidenciaDto
import com.mycar.app.features.soporte.data.SolicitudDetalleDto
import com.mycar.app.features.soporte.data.SolicitudResumenDto

interface AdminGeneralesRepository {
    suspend fun listar(estado: String?, q: String?): List<SolicitudResumenDto>
    suspend fun contadores(): ContadoresIncidenciaDto
    suspend fun detalle(codigo: String): SolicitudDetalleDto
    suspend fun aceptar(codigo: String, observacion: String?): AdminAccionResponse
    suspend fun iniciarAtencion(codigo: String, observacion: String?): AdminAccionResponse
    suspend fun terminar(codigo: String, respuesta: String): AdminAccionResponse
    suspend fun rechazar(codigo: String, motivo: String): AdminAccionResponse
    suspend fun descargarEvidencia(codigo: String): ByteArray
}