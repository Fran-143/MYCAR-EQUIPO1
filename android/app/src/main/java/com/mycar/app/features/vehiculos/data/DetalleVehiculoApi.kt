package com.mycar.app.features.vehiculos.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

data class DetalleVehiculoDto(
    val idVehiculo: Long, val placa: String, val marca: String, val modelo: String,
    val anioFabricacion: Int, val usoVehiculo: String, val color: String,
    val tipoMotor: String, val combustible: String?, val estadoVehiculo: String,
    val principal: Boolean, val kilometrajeActual: Double?, val fechaUltimaLecturaKm: String?,
    val numeroVin: String?, val numeroSerie: String?, val numeroMotor: String?,
    val tipoAceite: String?, val motorElectrico: Boolean,
    val usoVehiculoCodigo: String? = null,
    val idTipoMotor: Long? = null,
    val idCombustible: Long? = null,
    val idTipoAceite: Long? = null,
    val tipoMotorOtro: String? = null,
    val combustibleOtro: String? = null,
    val tipoAceiteOtro: String? = null,
    val tieneTive: Boolean = false,
    val tieneFoto: Boolean = false
)

data class SolicitudActualizacionVehiculoDto(
    val marca: String,
    val modelo: String,
    val usoVehiculoCodigo: String,
    val anioFabricacion: Int,
    val color: String,
    val idTipoMotor: Long,
    val idCombustible: Long?,
    val numeroVin: String?,
    val numeroSerie: String?,
    val numeroMotor: String?,
    val idTipoAceite: Long?,
    val tipoMotorOtro: String?,
    val combustibleOtro: String?,
    val tipoAceiteOtro: String?
)

data class SolicitudEstadoVehiculoDto(val estadoVehiculo: String)

interface DetalleVehiculoApi {
    @GET("api/v1/vehiculos/{idVehiculo}")
    suspend fun consultar(@Path("idVehiculo") idVehiculo: Long): Response<DetalleVehiculoDto>

    @PUT("api/v1/vehiculos/{idVehiculo}")
    suspend fun actualizar(
        @Path("idVehiculo") idVehiculo: Long,
        @Body solicitud: SolicitudActualizacionVehiculoDto
    ): Response<DetalleVehiculoDto>

    @PUT("api/v1/vehiculos/{idVehiculo}/principal")
    suspend fun establecerPrincipal(@Path("idVehiculo") idVehiculo: Long): Response<DetalleVehiculoDto>

    @PUT("api/v1/vehiculos/{idVehiculo}/estado")
    suspend fun cambiarEstado(
        @Path("idVehiculo") idVehiculo: Long,
        @Body solicitud: SolicitudEstadoVehiculoDto
    ): Response<DetalleVehiculoDto>
}
