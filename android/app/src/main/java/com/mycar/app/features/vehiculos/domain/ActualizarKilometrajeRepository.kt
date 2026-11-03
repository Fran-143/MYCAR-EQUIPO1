package com.mycar.app.features.vehiculos.domain

import com.mycar.app.features.vehiculos.data.ContextoKilometrajeDto
import com.mycar.app.features.vehiculos.data.EstadoOperacionKmDto
import com.mycar.app.features.vehiculos.data.ResultadoActualizarKmDto
import com.mycar.app.features.vehiculos.data.SolicitudActualizarKmDto
import java.util.UUID

interface ActualizarKilometrajeRepository {
    suspend fun contexto(idVehiculo: Long): ContextoKilometrajeDto
    suspend fun registrar(idVehiculo: Long, solicitud: SolicitudActualizarKmDto): ResultadoActualizarKmDto
    suspend fun operacion(idOperacion: UUID): EstadoOperacionKmDto
}
