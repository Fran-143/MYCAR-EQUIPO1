package com.mycar.app.features.recordatorioskm.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import java.math.BigDecimal

interface RecordatoriosKmApi {
    @GET("api/v1/recordatorios-km")
    suspend fun consultar(): Response<ConfiguracionRecordatoriosKmDto>

    @PATCH("api/v1/recordatorios-km")
    suspend fun actualizarGlobal(@Body body: EstadoGlobalRequestDto): Response<ConfiguracionRecordatoriosKmDto>

    @PUT("api/v1/recordatorios-km/vehiculos/{id}")
    suspend fun guardarFrecuencia(
        @Path("id") idVehiculo: Long,
        @Body body: FrecuenciaRequestDto
    ): Response<PreferenciaVehiculoDto>
}

data class ConfiguracionRecordatoriosKmDto(
    val estadoRecordatorios: String? = null,
    val vehiculos: List<VehiculoRecordatorioDto>? = null
)

data class VehiculoRecordatorioDto(
    val idVehiculo: Long = 0,
    val marca: String? = null,
    val modelo: String? = null,
    val placa: String? = null,
    val tieneFoto: Boolean = false,
    val kilometrajeActual: BigDecimal? = null,
    val frecuencia: String? = null,
    val proximoRecordatorio: String? = null,
    val sinLecturaValida: Boolean = false,
    val pendiente: Boolean = false
)

data class EstadoGlobalRequestDto(val estadoRecordatorios: String)
data class FrecuenciaRequestDto(val frecuencia: String)

data class PreferenciaVehiculoDto(
    val idVehiculo: Long = 0,
    val frecuencia: String? = null,
    val proximoRecordatorio: String? = null,
    val sinLecturaValida: Boolean = false,
    val pendiente: Boolean = false
)

data class ErrorRecordatorioKmDto(
    val codigo: String? = null,
    val mensaje: String? = null
)
