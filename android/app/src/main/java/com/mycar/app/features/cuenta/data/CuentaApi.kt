package com.mycar.app.features.cuenta.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.UUID

data class CuentaOtpBody(val codigo: String)
data class ConfirmarEliminacionBody(
    val codigo: String,
    val motivoCodigo: String? = null,
    val motivoTexto: String? = null
)
data class ReactivarInicioBody(
    val correo: String,
    val contrasena: String,
    val idInstalacion: UUID
)
data class ReactivarOtpBody(val correo: String, val codigo: String)
data class AsistidaCorreoBody(val correo: String)
data class AsistidaConfirmarBody(val correo: String, val codigo: String)

interface CuentaApi {
    @POST("api/v1/cuenta/eliminacion/iniciar")
    suspend fun iniciarEliminacion(): Response<Map<String, Any>>

    @POST("api/v1/cuenta/eliminacion/otp")
    suspend fun otpEliminacion(@Body body: CuentaOtpBody): Response<Map<String, Any>>

    @POST("api/v1/cuenta/eliminacion/confirmar")
    suspend fun confirmarEliminacion(@Body body: ConfirmarEliminacionBody): Response<Map<String, Any>>

    @POST("api/v1/cuenta/reactivacion/iniciar")
    suspend fun iniciarReactivacion(@Body body: ReactivarInicioBody): Response<Map<String, Any>>

    @POST("api/v1/cuenta/reactivacion/otp")
    suspend fun otpReactivacion(@Body body: ReactivarOtpBody): Response<Map<String, Any>>

    @POST("api/v1/cuenta/reactivacion/confirmar")
    suspend fun confirmarReactivacion(@Body body: ReactivarOtpBody): Response<Map<String, Any>>

    @POST("api/v1/cuenta/reactivacion/asistida/estado")
    suspend fun estadoAsistida(@Body body: AsistidaCorreoBody): Response<Map<String, Any>>

    @POST("api/v1/cuenta/reactivacion/asistida/confirmar-titular")
    suspend fun confirmarTitularAsistida(@Body body: AsistidaConfirmarBody): Response<Map<String, Any>>
}
