package com.mycar.app.features.localtest

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class LocalTestEstadoDto(
    val habilitado: Boolean = false,
    val simuladorCorreo: Boolean = false,
    val soloFixtures: Boolean = true
)

data class LocalTestCorreosPageDto(val items: List<LocalTestCorreoDto> = emptyList())

data class LocalTestCorreoDto(
    val idCorreo: Long = 0,
    val destinatario: String? = null,
    val tipoMensaje: String? = null,
    val asunto: String? = null,
    val cuerpo: String? = null,
    val codigoOtp: String? = null,
    val finalidadOtp: String? = null,
    val estado: String? = null,
    val creadoUtc: String? = null,
    val expiraOtpUtc: String? = null
)

data class LocalTestFinalidadBody(val destinatario: String, val finalidad: String)

interface LocalTestApi {
    @GET("api/v1/local-test/estado")
    suspend fun estado(@Header("X-MyCar-Local-Test") token: String): Response<LocalTestEstadoDto>

    @GET("api/v1/local-test/correos")
    suspend fun correos(
        @Header("X-MyCar-Local-Test") token: String,
        @Query("destinatario") destinatario: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<LocalTestCorreosPageDto>

    @GET("api/v1/local-test/correos/{id}")
    suspend fun detalle(
        @Header("X-MyCar-Local-Test") token: String,
        @Path("id") id: Long
    ): Response<LocalTestCorreoDto>

    @POST("api/v1/local-test/correos/ultimo-otp")
    suspend fun ultimoOtp(
        @Header("X-MyCar-Local-Test") token: String,
        @Body body: LocalTestFinalidadBody
    ): Response<LocalTestCorreoDto>

    @POST("api/v1/local-test/fixtures/restablecer-usuarios")
    suspend fun restablecerUsuarios(@Header("X-MyCar-Local-Test") token: String): Response<Map<String, Any>>

    @POST("api/v1/local-test/fixtures/preparar-rf51-pendiente")
    suspend fun prepararRf51(@Header("X-MyCar-Local-Test") token: String): Response<Map<String, Any>>

    @POST("api/v1/local-test/fixtures/preparar-limite-eliminacion")
    suspend fun prepararLimite(@Header("X-MyCar-Local-Test") token: String): Response<Map<String, Any>>
}
