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

class ProbarPushResponse(
    val ok: Boolean = false,
    val tokens: Int = 0,
    val enviados: Int = 0,
    val invalidos: Int = 0,
    val errores: Int = 0
)

class ProbarKmPushResponse(
    val ok: Boolean = false,
    val bandejaCreada: Boolean = false,
    val idNotificacion: Long? = null,
    val idVehiculo: Long? = null,
    val tokens: Int = 0,
    val enviados: Int = 0,
    val invalidos: Int = 0,
    val errores: Int = 0
)

/** Token vivo del dispositivo para upsert + envío en la misma petición de prueba. */
class ProbarPushBody(
    val idInstalacion: String? = null,
    val token: String? = null
)

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

    @POST("api/v1/dispositivos-push/probar")
    suspend fun probar(
        @Header("Authorization") bearer: String,
        @Body body: ProbarPushBody = ProbarPushBody()
    ): Response<ProbarPushResponse>

    @POST("api/v1/dispositivos-push/probar-km")
    suspend fun probarKm(
        @Header("Authorization") bearer: String,
        @Body body: ProbarPushBody = ProbarPushBody()
    ): Response<ProbarKmPushResponse>
}
