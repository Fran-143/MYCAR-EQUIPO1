package com.mycar.app.features.mantenimiento.domain

import com.mycar.app.features.mantenimiento.data.*

interface MantenimientoRepository {
    suspend fun contexto(): ContextoRf17Dto
    suspend fun lecturas(vehiculo: Long, fecha: String): List<LecturaRf17Dto>
    suspend fun registrar(vehiculo: Long, solicitud: SolicitudRf17Dto): ResultadoRf17Dto
    suspend fun consultar(idOperacion: String): EstadoRf17Dto
}

class ErrorMantenimientoRemoto(
    val codigo: String, override val message: String,
    val incierto: Boolean = false, val campos: Map<String,String> = emptyMap()
) : Exception(message)
