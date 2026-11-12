package com.mycar.app.features.push.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

class RegistrarTokenRequest(
    val idInstalacion: String,
    val proveedor: String,
    val token: String
)

class DesvincularTokenRequest(val idInstalacion: String)

class OkPushResponse(val ok: Boolean = false)

interface PushApi {
    @POST("api/v1/dispositivos-push/registrar")
    suspend fun registrar(
        @Header("Authorization") bearer: String,
        @Body body: RegistrarTokenRequest
    ): Response<OkPushResponse>

    @POST("api/v1/dispositivos-push/desvincular")
    suspend fun desvincular(
        @Header("Authorization") bearer: String,
        @Body body: DesvincularTokenRequest
    ): Response<Unit>
}
