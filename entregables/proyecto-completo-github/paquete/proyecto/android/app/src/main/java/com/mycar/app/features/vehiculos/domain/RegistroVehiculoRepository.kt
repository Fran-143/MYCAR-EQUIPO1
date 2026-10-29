package com.mycar.app.features.vehiculos.domain

import com.mycar.app.features.vehiculos.data.*

interface RegistroVehiculoRepository {
    suspend fun catalogos(): CatalogosRegistroDto
    suspend fun cupo(): CupoRegistroDto
    suspend fun registrar(solicitud: SolicitudRegistroVehiculoDto): ResultadoRegistroVehiculoDto
    suspend fun operacion(idOperacion: String): EstadoOperacionRegistroDto
}

/** Una instancia por cuenta autenticada; la clave de cuenta nunca autoriza peticiones HTTP. */
interface SolicitudRegistroPendienteStore {
    fun cargar(): SolicitudRegistroVehiculoDto?
    fun guardar(solicitud: SolicitudRegistroVehiculoDto)
    fun eliminar(idOperacion: String)
}

class FalloRegistroVehiculo(
    val codigo: String,
    val estadoHttp: Int? = null,
    val campos: Set<String> = emptySet()
) : RuntimeException(codigo)
