package com.mycar.app.features.vehiculos.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.UUID

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

data class ResumenDependenciasEliminacionDto(
    val mantenimientos: Long = 0,
    val lecturasKm: Long = 0,
    val alertas: Long = 0,
    val soat: Long = 0,
    val tive: Long = 0,
    val tieneFoto: Boolean = false
)

data class PrepararEliminacionDto(
    val idVehiculo: Long = 0,
    val placa: String? = null,
    val marca: String? = null,
    val modelo: String? = null,
    val estadoVehiculo: String? = null,
    val principal: Boolean = false,
    val versionRegistro: Int = 0,
    val quedaraSinPrincipal: Boolean = false,
    val idPrincipalCandidato: Long? = null,
    val placaPrincipalCandidato: String? = null,
    val marcaPrincipalCandidato: String? = null,
    val modeloPrincipalCandidato: String? = null,
    val dependencias: ResumenDependenciasEliminacionDto? = null,
    val mensajeConsecuenciaPrincipal: String? = null
)

data class ConfirmarEliminacionBodyDto(
    val idOperacion: UUID,
    val versionRegistro: Int
)

data class ConfirmarEliminacionDto(
    val idVehiculo: Long = 0,
    val estadoRegistro: String? = null,
    val principalResultanteExiste: Boolean = false,
    val idPrincipalResultante: Long? = null,
    val mensaje: String? = null
)

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

    @GET("api/v1/vehiculos/{idVehiculo}/eliminacion")
    suspend fun prepararEliminacion(@Path("idVehiculo") idVehiculo: Long): Response<PrepararEliminacionDto>

    @POST("api/v1/vehiculos/{idVehiculo}/eliminacion")
    suspend fun confirmarEliminacion(
        @Path("idVehiculo") idVehiculo: Long,
        @Body body: ConfirmarEliminacionBodyDto
    ): Response<ConfirmarEliminacionDto>
}
