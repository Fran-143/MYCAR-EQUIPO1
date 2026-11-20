package com.mycar.app.features.notificaciones.data

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificacionesApi {
    @GET("api/v1/notificaciones")
    suspend fun bandeja(
        @Query("pestana") pestana: String,
        @Query("categoria") categoria: String,
        @Query("filtroLectura") filtroLectura: String
    ): BandejaDto

    @GET("api/v1/notificaciones/{idNotificacion}")
    suspend fun detalle(@Path("idNotificacion") idNotificacion: Long): DetalleNotificacionDto

    @POST("api/v1/notificaciones/{idNotificacion}/leer")
    suspend fun leer(@Path("idNotificacion") idNotificacion: Long): LeerDto
}

data class NotificacionDto(
    val idNotificacion: Long?,
    val categoria: String?,
    val estadoGestion: String?,
    val leida: Boolean?,
    val titulo: String?,
    val cuerpo: String?,
    val destino: String?,
    val idAlerta: Long?,
    val idVehiculo: Long?,
    val fechaEmisionUtc: String?,
    val estadoAlerta: String? = null,
    val placa: String? = null,
    val vehiculoEtiqueta: String? = null
)

data class BandejaDto(
    val pestana: String?,
    val categoria: String?,
    val filtroLectura: String?,
    val contadorPendientesGlobal: Int?,
    val contadorNoLeidasGlobal: Int?,
    val contadorLeidasGlobal: Int? = null,
    val notificaciones: List<NotificacionDto>?
)

data class LeerDto(
    val idNotificacion: Long?,
    val leida: Boolean?,
    val estadoGestion: String?,
    val destino: String?,
    val idAlerta: Long?,
    val idVehiculo: Long?
)

data class DetalleNotificacionDto(
    val idNotificacion: Long?,
    val categoria: String?,
    val estadoGestion: String?,
    val leida: Boolean?,
    val titulo: String?,
    val cuerpo: String?,
    val destino: String?,
    val idAlerta: Long?,
    val idVehiculo: Long?,
    val fechaEmisionUtc: String?,
    val nombreEvento: String?,
    val vehiculoEtiqueta: String?,
    val estadoActual: String?,
    val fechaObjetivo: String?,
    val diasRestantes: Int?,
    val diasTranscurridos: Int?,
    val kmRestantes: Int?,
    val kmExcedidos: Int?,
    val kmObjetivo: Int?,
    val frecuenciaKm: String?,
    val ultimaActualizacionKm: String?,
    val recordatorioCorrespondienteKm: String?,
    val textoTemporal: String?,
    val textoKilometraje: String?,
    val accionDisponible: String?,
    val accionHabilitada: Boolean?
)

data class ErrorNotificacionDto(val codigo: String?, val mensaje: String?, val resultadoIncierto: Boolean?)
