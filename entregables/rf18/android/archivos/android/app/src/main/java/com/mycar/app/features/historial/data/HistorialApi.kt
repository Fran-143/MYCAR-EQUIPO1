package com.mycar.app.features.historial.data

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Propuesta RF18: se crea sobre el Retrofit autenticado compartido del equipo. */
interface HistorialApi {
    @GET("api/v1/rf18/contexto")
    suspend fun contexto(): Response<ContextoHistorialDto>

    @GET("api/v1/rf18/vehiculos/{id}/mantenimientos")
    suspend fun historial(@Path("id") id: Long): Response<ResultadoHistorialDto>
}

data class VehiculoHistorialDto(val idVehiculo: Long?, val placa: String?, val marca: String?,
    val modelo: String?, val estado: String?, val principal: Boolean?)
data class MantenimientoHistorialDto(val idMantenimiento: Long?, val codigoTipo: String?, val tipo: String?,
    val servicioOtro: String?, val fechaServicio: String?, val kmServicio: Int?, val estado: String?,
    val costoTotal: JsonElement?, val taller: String? = null)
data class ContextoHistorialDto(val vehiculos: List<VehiculoHistorialDto?>?)
data class ResultadoHistorialDto(val vehiculo: VehiculoHistorialDto?, val total: Int?,
    val mantenimientos: List<MantenimientoHistorialDto?>?)
