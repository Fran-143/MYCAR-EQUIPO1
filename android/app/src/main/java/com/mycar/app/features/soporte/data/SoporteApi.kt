package com.mycar.app.features.soporte.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface SoporteApi {
    @GET("api/v1/soporte/vehiculos-elegibles")
    suspend fun vehiculosElegibles(): Response<List<VehiculoElegibleDto>>

    @GET("api/v1/soporte/solicitudes")
    suspend fun listarSolicitudes(): Response<List<SolicitudResumenDto>>

    @GET("api/v1/soporte/resumen")
    suspend fun resumen(): Response<ResumenSoporteDto>

    @GET("api/v1/soporte/solicitudes/{codigo}")
    suspend fun detalle(@Path("codigo") codigo: String): Response<SolicitudDetalleDto>

    @GET("api/v1/soporte/solicitudes/{codigo}/evidencia")
    suspend fun evidencia(@Path("codigo") codigo: String): Response<ResponseBody>

    @Multipart
    @POST("api/v1/soporte/solicitudes")
    suspend fun crear(
        @Part("idOperacion") idOperacion: RequestBody,
        @Part("idVehiculo") idVehiculo: RequestBody,
        @Part("vinPropuesto") vinPropuesto: RequestBody,
        @Part("motivo") motivo: RequestBody,
        @Part file: MultipartBody.Part? = null,
        @Part("contentType") contentType: RequestBody? = null
    ): Response<CrearSolicitudResponse>

    @Multipart
    @POST("api/v1/soporte/incidencias-generales")
    suspend fun crearIncidencia(
        @Part("idOperacion") idOperacion: RequestBody,
        @Part("categoria") categoria: RequestBody,
        @Part("temaOtro") temaOtro: RequestBody?,
        @Part("asunto") asunto: RequestBody,
        @Part("descripcion") descripcion: RequestBody,
        @Part file: MultipartBody.Part? = null,
        @Part("contentType") contentType: RequestBody? = null
    ): Response<CrearSolicitudResponse>
}
