package com.mycar.app.features.autenticacion.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

// Clases deliberadamente sin toString generado: contienen secretos transitorios.
class LoginRequest(val correo: String, val contrasena: String, val idInstalacion: String)
class RenewalRequest(val idInstalacion: String, val refreshToken: String)
class TokenResponse(
    val estado: String? = null,
    val idSesion: Long? = null,
    val accessToken: String? = null,
    val accessTokenExpiraUtc: String? = null,
    val refreshToken: String? = null,
    val sesionExpiraUtc: String? = null
)
class SessionResponse(
    val vigente: Boolean = false,
    val idSesion: Long = 0,
    val sesionExpiraUtc: String? = null
)

interface AuthApi {
    @POST("api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") bearer: String): Response<Unit>

    @POST("api/v1/auth/registro")
    suspend fun register(@Body request: RegistrationRequest): Response<RegistrationResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>
    @POST("api/v1/auth/renovar")
    suspend fun renew(@Body request: RenewalRequest): Response<TokenResponse>
    @GET("api/v1/auth/sesion")
    suspend fun session(@Header("Authorization") bearer: String): Response<SessionResponse>
}
