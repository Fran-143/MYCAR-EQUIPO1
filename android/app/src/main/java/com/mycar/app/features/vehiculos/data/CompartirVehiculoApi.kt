package com.mycar.app.features.vehiculos.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface CompartirVehiculoApi {
    @Streaming
    @GET("api/v1/vehiculos/{idVehiculo}/compartir/pdf")
    suspend fun descargarPdf(
        @Path("idVehiculo") idVehiculo: Long,
        @Query("incluirFoto") incluirFoto: Boolean
    ): Response<ResponseBody>
}
