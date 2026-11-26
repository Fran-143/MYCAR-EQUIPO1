package com.mycar.app.features.vehiculos.domain

import com.mycar.app.features.vehiculos.data.CatalogosRegistroDto
import com.mycar.app.features.vehiculos.data.DetalleVehiculoDto
import com.mycar.app.features.vehiculos.data.SolicitudActualizacionVehiculoDto

interface EditarVehiculoRepository {
    suspend fun catalogos(): CatalogosRegistroDto
    suspend fun consultar(idVehiculo: Long): DetalleVehiculoDto
    suspend fun actualizar(idVehiculo: Long, solicitud: SolicitudActualizacionVehiculoDto): DetalleVehiculoDto
}
