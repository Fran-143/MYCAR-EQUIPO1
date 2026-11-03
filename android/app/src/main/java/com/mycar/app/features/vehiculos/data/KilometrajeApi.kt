package com.mycar.app.features.vehiculos.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.UUID

data class ContextoKilometrajeDto(
    val idVehiculo: Long, val idLectura: Long, val kmActual: Int,
    val fechaLectura: String?, val versionKm: Int, val estadoVehiculo: String
)
data class SolicitudActualizarKmDto(
    val idOperacion: UUID, val kmNuevo: Int, val idLecturaBase: Long, val versionBase: Int
)
data class ResultadoActualizarKmDto(
    val idOperacion: UUID, val idVehiculo: Long, val idLectura: Long,
    val kmActual: Int, val fechaServidor: String?, val estadoOperacion: String
)
data class EstadoOperacionKmDto(
    val idOperacion: UUID, val estado: String, val resultado: ResultadoActualizarKmDto?
)

interface KilometrajeApi {
    @GET("api/v1/vehiculos/{idVehiculo}/kilometraje/actual")
    suspend fun contexto(@Path("idVehiculo") idVehiculo: Long): Response<ContextoKilometrajeDto>

    @POST("api/v1/vehiculos/{idVehiculo}/kilometraje/lecturas")
    suspend fun registrar(
        @Path("idVehiculo") idVehiculo: Long,
        @Body solicitud: SolicitudActualizarKmDto
    ): Response<ResultadoActualizarKmDto>

    @GET("api/v1/vehiculos/kilometraje/operaciones/{idOperacion}")
    suspend fun operacion(@Path("idOperacion") idOperacion: UUID): Response<EstadoOperacionKmDto>
}
