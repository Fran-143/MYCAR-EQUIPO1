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

/** RF15: ítem de historial de lecturas. */
data class LecturaKilometrajeDto(
    val idLectura: Long,
    val valorKm: Double,
    val origen: String,
    val fechaLecturaUtc: String? = null,
    val kmAnteriorCorreccion: Double? = null,
    val motivoCorreccion: String? = null,
    val fechaRectificacionUtc: String? = null
)

/** RF16: contexto para corregir la última lectura. */
data class ContextoCorreccionKmDto(
    val idVehiculo: Long,
    val placa: String,
    val marca: String,
    val modelo: String,
    val idLectura: Long,
    val kmPenultimo: Double?,
    val kmUltimo: Double?,
    val versionKm: Int,
    val elegible: Boolean,
    val motivoNoElegible: String? = null
)

data class SolicitudCorregirKmDto(
    val idOperacion: UUID,
    val kmCorregido: Int,
    val motivo: String,
    val idLecturaBase: Long,
    val versionBase: Int
)

data class ResultadoCorregirKmDto(
    val idOperacion: UUID,
    val idVehiculo: Long,
    val idLectura: Long,
    val idCorreccion: Long,
    val kmAnterior: Double,
    val kmActual: Double,
    val kmPenultimo: Double,
    val motivo: String,
    val fechaRectificacion: String?,
    val fechaLecturaOriginal: String?,
    val estadoOperacion: String
)

data class EstadoOperacionCorreccionKmDto(
    val idOperacion: UUID,
    val estado: String,
    val resultado: ResultadoCorregirKmDto?
)

interface KilometrajeApi {
    @GET("api/v1/vehiculos/{idVehiculo}/kilometraje/actual")
    suspend fun contexto(@Path("idVehiculo") idVehiculo: Long): Response<ContextoKilometrajeDto>

    @GET("api/v1/vehiculos/{idVehiculo}/kilometraje/lecturas")
    suspend fun listarLecturas(@Path("idVehiculo") idVehiculo: Long): Response<List<LecturaKilometrajeDto>>

    @POST("api/v1/vehiculos/{idVehiculo}/kilometraje/lecturas")
    suspend fun registrar(
        @Path("idVehiculo") idVehiculo: Long,
        @Body solicitud: SolicitudActualizarKmDto
    ): Response<ResultadoActualizarKmDto>

    @GET("api/v1/vehiculos/kilometraje/operaciones/{idOperacion}")
    suspend fun operacion(@Path("idOperacion") idOperacion: UUID): Response<EstadoOperacionKmDto>

    @GET("api/v1/vehiculos/{idVehiculo}/kilometraje/lecturas/{idLectura}/correccion")
    suspend fun contextoCorreccion(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idLectura") idLectura: Long
    ): Response<ContextoCorreccionKmDto>

    @POST("api/v1/vehiculos/{idVehiculo}/kilometraje/lecturas/{idLectura}/correcciones")
    suspend fun corregir(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idLectura") idLectura: Long,
        @Body solicitud: SolicitudCorregirKmDto
    ): Response<ResultadoCorregirKmDto>

    @GET("api/v1/vehiculos/kilometraje/correcciones/operaciones/{idOperacion}")
    suspend fun operacionCorreccion(@Path("idOperacion") idOperacion: UUID): Response<EstadoOperacionCorreccionKmDto>
}
