package com.mycar.app.features.licencia.domain

import com.mycar.app.features.licencia.data.*

interface LicenciaRepository {
    suspend fun consultar(): LicenciaConsultaResponse
    suspend fun registrar(solicitud: LicenciaRegistrarRequest): LicenciaDto
    suspend fun editar(solicitud: LicenciaEditarRequest): LicenciaDto
    suspend fun renovar(solicitud: LicenciaRenovarRequest): LicenciaDto
    suspend fun recordatorio(activo: Boolean): LicenciaDto
    suspend fun subirFoto(lado: String, bytes: ByteArray, contentType: String, nombre: String): LicenciaFotoDto
    suspend fun descargarFoto(lado: String): ByteArray
}
