package com.mycar.app.features.vehiculos.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Crear usando el Retrofit autenticado que acordemos con Elsa. No incorpora credenciales. */
interface RegistroVehiculoApi {
    @GET("api/v1/vehiculos/catalogos")
    suspend fun catalogos(): Response<CatalogosRegistroDto>
    @GET("api/v1/vehiculos/cupo-registro")
    suspend fun cupo(): Response<CupoRegistroDto>
    @POST("api/v1/vehiculos")
    suspend fun registrar(@Body solicitud: SolicitudRegistroVehiculoDto): Response<ResultadoRegistroVehiculoDto>
    @GET("api/v1/vehiculos/operaciones/{idOperacion}")
    suspend fun operacion(@Path("idOperacion") idOperacion: String): Response<EstadoOperacionRegistroDto>
}
