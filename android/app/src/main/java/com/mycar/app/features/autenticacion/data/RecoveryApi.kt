package com.mycar.app.features.autenticacion.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class RecoveryEmailRequest(val correo: String)
data class RecoveryVerifyRequest(val correo: String, val codigo: String)
data class RecoveryResetRequest(
    val correo: String,
    val codigo: String,
    val nuevaContrasena: String,
    val confirmacionContrasena: String
)

data class RecoveryEmitResponse(
    val resultado: String? = null,
    val mensaje: String? = null,
    val codigoDesarrollo: String? = null
)

data class RecoverySimpleResponse(val resultado: String? = null, val destino: String? = null)

data class RecoveryErrorDto(
    val codigo: String? = null,
    val mensaje: String? = null,
    val erroresPorCampo: Map<String, String>? = null
)

interface RecoveryApi {
    @POST("api/v1/auth/recuperacion/emitir")
    suspend fun emitir(@Body body: RecoveryEmailRequest): Response<RecoveryEmitResponse>

    @POST("api/v1/auth/recuperacion/verificar")
    suspend fun verificar(@Body body: RecoveryVerifyRequest): Response<RecoverySimpleResponse>

    @POST("api/v1/auth/recuperacion/contrasena")
    suspend fun restablecer(@Body body: RecoveryResetRequest): Response<RecoverySimpleResponse>
}
