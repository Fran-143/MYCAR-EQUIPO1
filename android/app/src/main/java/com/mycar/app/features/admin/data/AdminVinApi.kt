package com.mycar.app.features.admin.data

import com.mycar.app.features.soporte.data.SolicitudDetalleDto
import com.mycar.app.features.soporte.data.SolicitudResumenDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class AdminRechazarBody(val motivo: String)

data class AdminAccionResponse(
    val codigo: String,
    val estado: String,
    val versionFila: Int = 0
)

interface AdminVinApi {
    @GET("api/v1/admin/incidencias/vin")
    suspend fun listar(
        @Query("estado") estado: String? = null,
        @Query("q") q: String? = null
    ): Response<List<SolicitudResumenDto>>

    @GET("api/v1/admin/incidencias/vin/{codigo}")
    suspend fun detalle(@Path("codigo") codigo: String): Response<SolicitudDetalleDto>

    @GET("api/v1/admin/incidencias/vin/{codigo}/evidencia")
    suspend fun evidencia(@Path("codigo") codigo: String): Response<ResponseBody>

    @POST("api/v1/admin/incidencias/vin/{codigo}/iniciar-revision")
    suspend fun iniciarRevision(@Path("codigo") codigo: String): Response<AdminAccionResponse>

    @POST("api/v1/admin/incidencias/vin/{codigo}/aprobar")
    suspend fun aprobar(@Path("codigo") codigo: String): Response<AdminAccionResponse>

    @POST("api/v1/admin/incidencias/vin/{codigo}/rechazar")
    suspend fun rechazar(
        @Path("codigo") codigo: String,
        @Body body: AdminRechazarBody
    ): Response<AdminAccionResponse>
}