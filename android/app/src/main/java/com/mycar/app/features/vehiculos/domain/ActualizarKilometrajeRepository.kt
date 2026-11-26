package com.mycar.app.features.vehiculos.domain

import com.mycar.app.features.vehiculos.data.ContextoCorreccionKmDto
import com.mycar.app.features.vehiculos.data.ContextoKilometrajeDto
import com.mycar.app.features.vehiculos.data.EstadoOperacionCorreccionKmDto
import com.mycar.app.features.vehiculos.data.EstadoOperacionKmDto
import com.mycar.app.features.vehiculos.data.LecturaKilometrajeDto
import com.mycar.app.features.vehiculos.data.ResultadoActualizarKmDto
import com.mycar.app.features.vehiculos.data.ResultadoCorregirKmDto
import com.mycar.app.features.vehiculos.data.SolicitudActualizarKmDto
import com.mycar.app.features.vehiculos.data.SolicitudCorregirKmDto
import java.util.UUID

interface ActualizarKilometrajeRepository {
    suspend fun contexto(idVehiculo: Long): ContextoKilometrajeDto
    /** RF15: historial de lecturas del vehículo (ACTIVO o INACTIVO). */
    suspend fun listarLecturas(idVehiculo: Long): List<LecturaKilometrajeDto>
    suspend fun registrar(idVehiculo: Long, solicitud: SolicitudActualizarKmDto): ResultadoActualizarKmDto
    suspend fun operacion(idOperacion: UUID): EstadoOperacionKmDto

    /** RF16 */
    suspend fun contextoCorreccion(idVehiculo: Long, idLectura: Long): ContextoCorreccionKmDto
    suspend fun corregir(idVehiculo: Long, idLectura: Long, solicitud: SolicitudCorregirKmDto): ResultadoCorregirKmDto
    suspend fun operacionCorreccion(idOperacion: UUID): EstadoOperacionCorreccionKmDto
}
