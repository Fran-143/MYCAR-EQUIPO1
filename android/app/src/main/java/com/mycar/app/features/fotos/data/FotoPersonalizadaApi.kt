package com.mycar.app.features.fotos.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

data class ResultadoFotoDto(
    val resultado: String? = null,
    val mensaje: String? = null
)

interface FotoPerfilApi {
    @Multipart
    @POST("api/v1/perfil/foto")
    suspend fun registrar(
        @Part file: MultipartBody.Part,
        @Part("contentType") contentType: RequestBody
    ): Response<ResultadoFotoDto>

    @Multipart
    @PUT("api/v1/perfil/foto")
    suspend fun actualizar(
        @Part file: MultipartBody.Part,
        @Part("contentType") contentType: RequestBody
    ): Response<ResultadoFotoDto>

    @GET("api/v1/perfil/foto")
    suspend fun descargar(): Response<ResponseBody>

    @DELETE("api/v1/perfil/foto")
    suspend fun eliminar(): Response<ResultadoFotoDto>
}

interface FotoVehiculoApi {
    @Multipart
    @POST("api/v1/vehiculos/{idVehiculo}/foto")
    suspend fun registrar(
        @Path("idVehiculo") idVehiculo: Long,
        @Part file: MultipartBody.Part,
        @Part("contentType") contentType: RequestBody
    ): Response<ResultadoFotoDto>

    @Multipart
    @PUT("api/v1/vehiculos/{idVehiculo}/foto")
    suspend fun actualizar(
        @Path("idVehiculo") idVehiculo: Long,
        @Part file: MultipartBody.Part,
        @Part("contentType") contentType: RequestBody
    ): Response<ResultadoFotoDto>

    @GET("api/v1/vehiculos/{idVehiculo}/foto")
    suspend fun descargar(@Path("idVehiculo") idVehiculo: Long): Response<ResponseBody>

    @DELETE("api/v1/vehiculos/{idVehiculo}/foto")
    suspend fun eliminar(@Path("idVehiculo") idVehiculo: Long): Response<ResultadoFotoDto>
}
