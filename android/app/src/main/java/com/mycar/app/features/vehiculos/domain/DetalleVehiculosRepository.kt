package com.mycar.app.features.vehiculos.domain

import com.mycar.app.features.vehiculos.data.ConfirmarEliminacionDto
import com.mycar.app.features.vehiculos.data.DetalleVehiculoDto
import com.mycar.app.features.vehiculos.data.PrepararEliminacionDto
import java.util.UUID

interface DetalleVehiculosRepository {
    suspend fun consultar(idVehiculo: Long): DetalleVehiculoDto
    suspend fun establecerPrincipal(idVehiculo: Long): DetalleVehiculoDto
    suspend fun cambiarEstado(idVehiculo: Long, estadoVehiculo: String): DetalleVehiculoDto
    suspend fun prepararEliminacion(idVehiculo: Long): PrepararEliminacionDto
    suspend fun confirmarEliminacion(
        idVehiculo: Long,
        idOperacion: UUID,
        versionRegistro: Int
    ): ConfirmarEliminacionDto
}
