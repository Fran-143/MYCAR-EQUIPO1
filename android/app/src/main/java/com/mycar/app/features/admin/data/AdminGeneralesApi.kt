package com.mycar.app.features.admin.data

import com.mycar.app.features.soporte.data.ContadoresIncidenciaDto
import com.mycar.app.features.soporte.data.SolicitudDetalleDto
import com.mycar.app.features.soporte.data.SolicitudResumenDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AdminGeneralesApi {
    @GET("api/v1/admin/incidencias/generales")
    suspend fun listar(
        @Query("estado") estado: String? = null,
        @Query("q") q: String? = null
    ): Response<List<SolicitudResumenDto>>

    @GET("api/v1/admin/incidencias/generales/contadores")
    suspend fun contadores(): Response<ContadoresIncidenciaDto>

    @GET("api/v1/admin/incidencias/generales/{codigo}")
    suspend fun detalle(@Path("codigo") codigo: String): Response<SolicitudDetalleDto>

    @GET("api/v1/admin/incidencias/generales/{codigo}/evidencia")
    suspend fun evidencia(@Path("codigo") codigo: String): Response<ResponseBody>

    @POST("api/v1/admin/incidencias/generales/{codigo}/aceptar")
    suspend fun aceptar(
        @Path("codigo") codigo: String,
        @Body body: AdminObservacionBody
    ): Response<AdminAccionResponse>

    @POST("api/v1/admin/incidencias/generales/{codigo}/iniciar-atencion")
    suspend fun iniciarAtencion(
        @Path("codigo") codigo: String,
        @Body body: AdminObservacionBody
    ): Response<AdminAccionResponse>

    @POST("api/v1/admin/incidencias/generales/{codigo}/terminar")
    suspend fun terminar(
        @Path("codigo") codigo: String,
        @Body body: AdminTerminarBody
    ): Response<AdminAccionResponse>

    @POST("api/v1/admin/incidencias/generales/{codigo}/rechazar")
    suspend fun rechazar(
        @Path("codigo") codigo: String,
        @Body body: AdminRechazarBody
    ): Response<AdminAccionResponse>
}