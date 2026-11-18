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
    val sesionExpiraUtc: String? = null,
    val alcance: String? = null,
    val desafioId: String? = null
)
class SessionResponse(
    val vigente: Boolean = false,
    val idSesion: Long = 0,
    val sesionExpiraUtc: String? = null,
    val alcance: String? = null,
    val rol: String? = null
)
class MfaEnrollStartResponse(
    val otpauthUri: String? = null,
    val secretoManual: String? = null
)
class MfaCodigoRequest(val codigo: String)
class MfaRecoveryResponse(
    val estado: String? = null,
    val codigosRecuperacion: List<String>? = null,
    val loteId: String? = null
)
class MfaVerificarRequest(val desafioId: String, val codigo: String)
class MfaRecuperarRequest(val desafioId: String, val codigoRespaldo: String)
class AdminHubResponse(
    val titulo: String? = null,
    val secciones: List<AdminSeccionDto>? = null
)
class AdminSeccionDto(
    val codigo: String? = null,
    val nombre: String? = null,
    val disponible: Boolean = false,
    val estado: String? = null
)

interface AuthApi {
    @POST("api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") bearer: String): Response<Unit>

    @POST("api/v1/auth/registro")
    suspend fun register(@Body request: RegistrationRequest): Response<RegistrationResponse>

    @POST("api/v1/auth/registro/verificacion/origen")
    suspend fun verificationOrigin(): Response<VerificationOriginResponse>

    @POST("api/v1/auth/registro/verificacion/emitir")
    suspend fun verificationEmit(@Body body: VerificationEmitRequest): Response<VerificationEmitResponse>

    @POST("api/v1/auth/registro/verificacion/reenviar")
    suspend fun verificationResend(@Body body: VerificationEmitRequest): Response<VerificationEmitResponse>

    @POST("api/v1/auth/registro/verificacion/completar")
    suspend fun verificationComplete(@Body body: VerificationCompleteRequest): Response<RegistrationResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>
    @POST("api/v1/auth/renovar")
    suspend fun renew(@Body request: RenewalRequest): Response<TokenResponse>
    @GET("api/v1/auth/sesion")
    suspend fun session(@Header("Authorization") bearer: String): Response<SessionResponse>

    @POST("api/v1/auth/mfa/enroll/iniciar")
    suspend fun mfaEnrollStart(@Header("Authorization") bearer: String): Response<MfaEnrollStartResponse>

    @POST("api/v1/auth/mfa/enroll/confirmar")
    suspend fun mfaEnrollConfirm(
        @Header("Authorization") bearer: String,
        @Body body: MfaCodigoRequest
    ): Response<MfaRecoveryResponse>

    @POST("api/v1/auth/mfa/enroll/confirmar-respaldo")
    suspend fun mfaEnrollConfirmBackup(@Header("Authorization") bearer: String): Response<TokenResponse>

    @POST("api/v1/auth/mfa/verificar")
    suspend fun mfaVerify(
        @Header("Authorization") bearer: String,
        @Body body: MfaVerificarRequest
    ): Response<TokenResponse>

    @POST("api/v1/auth/mfa/recuperar")
    suspend fun mfaRecover(
        @Header("Authorization") bearer: String,
        @Body body: MfaRecuperarRequest
    ): Response<TokenResponse>

    @GET("api/v1/admin/hub")
    suspend fun adminHub(@Header("Authorization") bearer: String): Response<AdminHubResponse>
}
