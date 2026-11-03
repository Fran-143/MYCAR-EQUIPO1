package com.mycar.app.features.vehiculos.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

data class DetalleVehiculoDto(
    val idVehiculo: Long, val placa: String, val marca: String, val modelo: String,
    val anioFabricacion: Int, val usoVehiculo: String, val color: String,
    val tipoMotor: String, val combustible: String?, val estadoVehiculo: String,
    val principal: Boolean, val kilometrajeActual: Int?, val fechaUltimaLecturaKm: String?,
    val numeroVin: String?, val numeroSerie: String?, val numeroMotor: String?,
    val tipoAceite: String?, val motorElectrico: Boolean
)

interface DetalleVehiculoApi {
    @GET("api/v1/vehiculos/{idVehiculo}")
    suspend fun consultar(@Path("idVehiculo") idVehiculo: Long): Response<DetalleVehiculoDto>
}
