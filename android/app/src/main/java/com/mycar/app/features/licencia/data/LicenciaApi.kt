package com.mycar.app.features.licencia.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

interface LicenciaApi {
    @GET("api/v1/licencia")
    suspend fun consultar(): Response<LicenciaConsultaResponse>

    @POST("api/v1/licencia")
    suspend fun registrar(@Body body: LicenciaRegistrarRequest): Response<LicenciaRegistrarResponse>

    @PUT("api/v1/licencia")
    suspend fun editar(@Body body: LicenciaEditarRequest): Response<LicenciaEditarResponse>

    @POST("api/v1/licencia/renovar")
    suspend fun renovar(@Body body: LicenciaRenovarRequest): Response<LicenciaRenovarResponse>

    @PUT("api/v1/licencia/recordatorio")
    suspend fun recordatorio(@Body body: LicenciaRecordatorioRequest): Response<LicenciaRecordatorioResponse>

    @Multipart
    @POST("api/v1/licencia/fotos/upload")
    suspend fun subirFoto(
        @Part("lado") lado: RequestBody,
        @Part file: MultipartBody.Part,
        @Part("contentType") contentType: RequestBody,
        @Part("nombreOriginal") nombreOriginal: RequestBody
    ): Response<LicenciaFotoDto>

    @GET("api/v1/licencia/fotos/{lado}")
    suspend fun descargarFoto(@Path("lado") lado: String): Response<ResponseBody>
}
