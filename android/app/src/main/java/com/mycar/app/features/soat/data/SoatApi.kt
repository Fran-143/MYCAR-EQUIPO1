package com.mycar.app.features.soat.data

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

interface SoatApi {
    @GET("api/v1/soat/aseguradoras")
    suspend fun listarAseguradoras(): Response<AseguradorasSoatListaDto>

    @GET("api/v1/vehiculos/{idVehiculo}/soat")
    suspend fun consultar(@Path("idVehiculo") idVehiculo: Long): Response<SoatConsultaResponse>

    @POST("api/v1/vehiculos/{idVehiculo}/soat")
    suspend fun registrar(
        @Path("idVehiculo") idVehiculo: Long,
        @Body solicitud: SoatMutacionDto
    ): Response<SoatRegistrarRespuestaDto>

    @PUT("api/v1/vehiculos/{idVehiculo}/soat/{idSoat}")
    suspend fun corregir(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idSoat") idSoat: Long,
        @Body solicitud: SoatMutacionDto
    ): Response<SoatCorregirRespuestaDto>

    @POST("api/v1/vehiculos/{idVehiculo}/soat/renovar")
    suspend fun renovar(
        @Path("idVehiculo") idVehiculo: Long,
        @Body solicitud: SoatMutacionDto
    ): Response<SoatRenovarRespuestaDto>

    @PUT("api/v1/vehiculos/{idVehiculo}/soat/{idSoat}/recordatorio")
    suspend fun recordatorio(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idSoat") idSoat: Long,
        @Body solicitud: SoatRecordatorioDto
    ): Response<SoatRecordatorioRespuestaDto>

    /**
     * Render actual responde JSON {downloadUrl}. Backend nuevo puede responder bytes.
     * Se parsea en el repositorio según Content-Type / cuerpo.
     */
    @GET("api/v1/vehiculos/{idVehiculo}/soat/{idSoat}/documento")
    suspend fun descargarDocumento(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idSoat") idSoat: Long
    ): Response<ResponseBody>

    @POST("api/v1/vehiculos/{idVehiculo}/soat/documentos/presign-upload")
    suspend fun presignUpload(
        @Path("idVehiculo") idVehiculo: Long,
        @Body solicitud: SoatPresignSolicitudDto
    ): Response<SoatPresignRespuestaDto>

    @Multipart
    @POST("api/v1/vehiculos/{idVehiculo}/soat/documentos/upload")
    suspend fun subirDocumentoMultipart(
        @Path("idVehiculo") idVehiculo: Long,
        @Part file: MultipartBody.Part,
        @Part("contentType") contentType: RequestBody,
        @Part("nombreOriginal") nombreOriginal: RequestBody
    ): Response<SoatDocumentoRefDto>
}
