package com.mycar.app.features.soat.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface SoatApi {
    @GET("api/v1/vehiculos/{idVehiculo}/soat")
    suspend fun consultar(@Path("idVehiculo") idVehiculo: Long): Response<SoatConsultaResponse>

    @POST("api/v1/vehiculos/{idVehiculo}/soat")
    suspend fun registrar(
        @Path("idVehiculo") idVehiculo: Long,
        @Body solicitud: SoatMutacionDto
    ): Response<SoatDto>

    @PUT("api/v1/vehiculos/{idVehiculo}/soat/{idSoat}")
    suspend fun corregir(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idSoat") idSoat: Long,
        @Body solicitud: SoatMutacionDto
    ): Response<SoatDto>

    @POST("api/v1/vehiculos/{idVehiculo}/soat/renovar")
    suspend fun renovar(
        @Path("idVehiculo") idVehiculo: Long,
        @Body solicitud: SoatMutacionDto
    ): Response<SoatDto>

    @PUT("api/v1/vehiculos/{idVehiculo}/soat/{idSoat}/recordatorio")
    suspend fun recordatorio(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idSoat") idSoat: Long,
        @Body solicitud: SoatRecordatorioDto
    ): Response<SoatDto>

    @POST("api/v1/vehiculos/{idVehiculo}/soat/documentos/presign-upload")
    suspend fun presignUpload(
        @Path("idVehiculo") idVehiculo: Long,
        @Body solicitud: SoatPresignSolicitudDto
    ): Response<SoatPresignRespuestaDto>

    @GET("api/v1/vehiculos/{idVehiculo}/soat/{idSoat}/documento")
    suspend fun descargarDocumento(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idSoat") idSoat: Long
    ): Response<SoatDocumentoDescargaDto>
}