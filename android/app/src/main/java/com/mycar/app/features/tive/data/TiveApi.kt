package com.mycar.app.features.tive.data

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

interface TiveApi {
    @GET("api/v1/vehiculos/{idVehiculo}/tive/precheck")
    suspend fun precheck(@Path("idVehiculo") idVehiculo: Long): Response<TivePrecheckResponse>

    @GET("api/v1/vehiculos/{idVehiculo}/tive")
    suspend fun consultar(@Path("idVehiculo") idVehiculo: Long): Response<TiveConsultaResponse>

    @POST("api/v1/vehiculos/{idVehiculo}/tive")
    suspend fun registrar(
        @Path("idVehiculo") idVehiculo: Long,
        @Body solicitud: TiveRegistrarRequest
    ): Response<TiveRegistrarResponse>

    @PUT("api/v1/vehiculos/{idVehiculo}/tive/{idTive}")
    suspend fun actualizar(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idTive") idTive: Long,
        @Body solicitud: TiveActualizarRequest
    ): Response<TiveActualizarResponse>

    @GET("api/v1/vehiculos/{idVehiculo}/tive/{idTive}/documento")
    suspend fun descargarDocumento(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idTive") idTive: Long
    ): Response<ResponseBody>

    @Multipart
    @POST("api/v1/vehiculos/{idVehiculo}/tive/documentos/upload")
    suspend fun subirDocumento(
        @Path("idVehiculo") idVehiculo: Long,
        @Part file: MultipartBody.Part,
        @Part("contentType") contentType: RequestBody,
        @Part("nombreOriginal") nombreOriginal: RequestBody
    ): Response<TiveDocumentoRefDto>
}