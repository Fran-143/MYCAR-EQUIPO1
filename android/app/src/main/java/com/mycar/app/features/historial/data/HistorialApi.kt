package com.mycar.app.features.historial.data

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Propuesta RF18 (+ RF19/RF20/RF21): sobre el Retrofit autenticado compartido. */
interface HistorialApi {
    @GET("api/v1/rf18/contexto")
    suspend fun contexto(): Response<ContextoHistorialDto>

    @GET("api/v1/rf18/vehiculos/{id}/mantenimientos")
    suspend fun historial(@Path("id") id: Long): Response<ResultadoHistorialDto>

    @GET("api/v1/rf18/vehiculos/{idVehiculo}/mantenimientos/{idMantenimiento}")
    suspend fun detalle(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idMantenimiento") idMantenimiento: Long
    ): Response<DetalleMantenimientoDto>

    @PATCH("api/v1/rf18/vehiculos/{idVehiculo}/mantenimientos/{idMantenimiento}")
    suspend fun modificar(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idMantenimiento") idMantenimiento: Long,
        @Body body: ModificarMantenimientoDto
    ): Response<DetalleMantenimientoDto>

    @POST("api/v1/rf18/vehiculos/{idVehiculo}/mantenimientos/{idMantenimiento}/anular")
    suspend fun anular(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idMantenimiento") idMantenimiento: Long
    ): Response<AnularMantenimientoDto>
}

data class VehiculoHistorialDto(val idVehiculo: Long?, val placa: String?, val marca: String?,
    val modelo: String?, val estado: String?, val principal: Boolean?)
data class MantenimientoHistorialDto(val idMantenimiento: Long?, val codigoTipo: String?, val tipo: String?,
    val servicioOtro: String?, val fechaServicio: String?, val kmServicio: Int?, val estado: String?,
    val costoTotal: JsonElement?, val taller: String? = null)
data class ContextoHistorialDto(val vehiculos: List<VehiculoHistorialDto?>?)
data class ResultadoHistorialDto(val vehiculo: VehiculoHistorialDto?, val total: Int?,
    val mantenimientos: List<MantenimientoHistorialDto?>?)

data class RepuestoDetalleDto(
    val nombre: String?, val marca: String?, val cantidad: Int?, val precioUnitario: JsonElement?
)
data class DetalleMantenimientoDto(
    val idMantenimiento: Long?,
    val idVehiculo: Long?,
    val placa: String? = null,
    val marca: String? = null,
    val modelo: String? = null,
    val estadoVehiculo: String? = null,
    val codigoTipo: String?,
    val tipo: String?,
    val servicioOtro: String?,
    val fechaServicio: String?,
    val kmServicio: Int?,
    val estado: String?,
    val costoTotal: JsonElement?,
    val observaciones: String?,
    val taller: String?,
    val idTaller: Long? = null,
    val proximaFechaSugerida: String? = null,
    val proximoKmSugerido: Int? = null,
    val anuladoUtc: String? = null,
    val editable: Boolean?,
    val anulable: Boolean?,
    val bloqueadoPorAlertaActiva: Boolean?,
    val repuestos: List<RepuestoDetalleDto?>?
)
data class ModificarMantenimientoDto(
    val costoTotal: String?,
    val observaciones: String?,
    val idTaller: Long? = null,
    val sinTaller: Boolean? = null
)
data class AnularMantenimientoDto(val idMantenimiento: Long?, val estado: String?, val anuladoUtc: String? = null)
data class ErrorHistorialDto(val codigo: String?, val mensaje: String?, val idAlerta: Long? = null)
