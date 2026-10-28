package com.mycar.app.features.sesion.data

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

class SolicitudLogin(val correo: String, val contrasena: String, val idInstalacion: String) {
    override fun toString() = "SolicitudLogin[REDACTED]"
}
class SolicitudRenovacion(val idInstalacion: String, val refreshToken: String) {
    override fun toString() = "SolicitudRenovacion[REDACTED]"
}
class TokensSesionDto(val estado: String?, val idSesion: Long, val accessToken: String?,
    val accessTokenExpiraUtc: String?, val refreshToken: String?, val sesionExpiraUtc: String?) {
    override fun toString() = "TokensSesionDto[REDACTED]"
}
data class IdentidadVehiculosDto(val idUsuario: Long)
interface SesionApi {
    @POST("api/v1/auth/login") suspend fun login(@Body solicitud: SolicitudLogin): Response<TokensSesionDto>
    @POST("api/v1/auth/renovar") fun renovar(@Body solicitud: SolicitudRenovacion): Call<TokensSesionDto>
    @GET("api/v1/vehiculos/identidad") suspend fun identidad(@Header("Authorization") autorizacion: String): Response<IdentidadVehiculosDto>
    @POST("api/v1/auth/logout") suspend fun cerrar(@Header("Authorization") autorizacion: String): Response<Void>
}
