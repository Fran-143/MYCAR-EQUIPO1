package com.mycar.app.features.vehiculos.domain

import com.mycar.app.features.vehiculos.data.DetalleVehiculoDto

interface DetalleVehiculosRepository {
    suspend fun consultar(idVehiculo: Long): DetalleVehiculoDto
}
