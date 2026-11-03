package com.mycar.app.features.vehiculos.data

import com.google.gson.Gson
import com.mycar.app.features.vehiculos.domain.DetalleVehiculosRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo

class RemoteDetalleVehiculosRepository(private val api: DetalleVehiculoApi) : DetalleVehiculosRepository {
    private data class ErrorApi(val codigo: String?)
    override suspend fun consultar(idVehiculo: Long): DetalleVehiculoDto {
        val r = api.consultar(idVehiculo)
        if (!r.isSuccessful) {
            val codigo = runCatching { r.errorBody()?.use { Gson().fromJson(it.string(), ErrorApi::class.java)?.codigo } }.getOrNull()
            throw FalloRegistroVehiculo(codigo ?: if (r.code() == 401) "SESION_NO_VALIDA" else "ERROR_HTTP", r.code())
        }
        val detalle = r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
        val valido = runCatching {
            detalle.idVehiculo == idVehiculo && detalle.idVehiculo > 0 && detalle.placa.isNotBlank() &&
                detalle.marca.isNotBlank() && detalle.modelo.isNotBlank() &&
                detalle.estadoVehiculo in setOf("ACTIVO", "INACTIVO") &&
                (detalle.kilometrajeActual == null || detalle.kilometrajeActual > 0)
        }.getOrDefault(false)
        if (!valido) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
        return detalle
    }
}
