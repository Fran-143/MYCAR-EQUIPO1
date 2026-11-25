package com.mycar.app.features.historial.export

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

data class TipoExportDto(
    val idTipo: Long = 0,
    val codigo: String? = null,
    val nombre: String? = null
)

data class ContextoExportDto(
    val premium: Boolean = false,
    val idVehiculo: Long = 0,
    val placa: String? = null,
    val marca: String? = null,
    val modelo: String? = null,
    val estadoVehiculo: String? = null,
    val kmVigente: Int? = null,
    val tipos: List<TipoExportDto> = emptyList()
)

data class ExportarHistorialBody(
    val fechaDesde: String,
    val fechaHasta: String,
    val tipoMantenimientoId: Long? = null,
    val incluirAnulados: Boolean = false
)

interface ExportarHistorialApi {
    @GET("api/v1/vehiculos/{idVehiculo}/historial/mantenimientos/exportar-pdf/contexto")
    suspend fun contexto(@Path("idVehiculo") idVehiculo: Long): Response<ContextoExportDto>

    @Streaming
    @POST("api/v1/vehiculos/{idVehiculo}/historial/mantenimientos/exportar-pdf")
    suspend fun exportarPdf(
        @Path("idVehiculo") idVehiculo: Long,
        @Body body: ExportarHistorialBody
    ): Response<ResponseBody>
}
