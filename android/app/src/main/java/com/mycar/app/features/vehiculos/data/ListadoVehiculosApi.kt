package com.mycar.app.features.vehiculos.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

data class VehiculoResumenDto(
    val idVehiculo: Long, val placa: String, val marca: String, val modelo: String,
    val anioFabricacion: Int, val usoVehiculo: String, val color: String,
    val tipoMotor: String, val combustible: String?, val estadoVehiculo: String,
    val principal: Boolean, val kilometrajeActual: Int?
)
data class ResumenEstadosDto(val todos: Long, val activos: Long, val inactivos: Long)
data class ListadoVehiculosDto(
    val vehiculos: List<VehiculoResumenDto>, val resumen: ResumenEstadosDto,
    val filtro: String, val busqueda: String
)

interface ListadoVehiculosApi {
    @GET("api/v1/vehiculos")
    suspend fun listar(@Query("filtro") filtro: String, @Query("busqueda") busqueda: String): Response<ListadoVehiculosDto>
}
