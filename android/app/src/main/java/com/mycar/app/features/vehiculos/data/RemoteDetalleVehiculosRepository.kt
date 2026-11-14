package com.mycar.app.features.vehiculos.data

import com.google.gson.Gson
import com.mycar.app.features.vehiculos.domain.DetalleVehiculosRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import retrofit2.Response

class RemoteDetalleVehiculosRepository(private val api: DetalleVehiculoApi) : DetalleVehiculosRepository {
    private data class ErrorApi(val codigo: String?, val mensaje: String? = null)

    override suspend fun consultar(idVehiculo: Long): DetalleVehiculoDto =
        interpretar(api.consultar(idVehiculo), idVehiculo)

    override suspend fun establecerPrincipal(idVehiculo: Long): DetalleVehiculoDto =
        interpretar(api.establecerPrincipal(idVehiculo), idVehiculo)

    override suspend fun cambiarEstado(idVehiculo: Long, estadoVehiculo: String): DetalleVehiculoDto =
        interpretar(api.cambiarEstado(idVehiculo, SolicitudEstadoVehiculoDto(estadoVehiculo)), idVehiculo)

    private fun interpretar(r: Response<DetalleVehiculoDto>, idVehiculo: Long): DetalleVehiculoDto {
        if (!r.isSuccessful) {
            val error = runCatching {
                r.errorBody()?.use { Gson().fromJson(it.string(), ErrorApi::class.java) }
            }.getOrNull()
            throw FalloRegistroVehiculo(
                error?.codigo ?: if (r.code() == 401) "SESION_NO_VALIDA" else "ERROR_HTTP",
                r.code(),
                mensajeServidor = error?.mensaje
            )
        }
        val detalle = r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        val valido = runCatching {
            detalle.idVehiculo == idVehiculo && detalle.idVehiculo > 0 &&
                !detalle.placa.isNullOrBlank() && !detalle.marca.isNullOrBlank() &&
                !detalle.modelo.isNullOrBlank() &&
                detalle.estadoVehiculo in setOf("ACTIVO", "INACTIVO") &&
                (detalle.kilometrajeActual == null || detalle.kilometrajeActual >= 0)
        }.getOrDefault(false)
        if (!valido) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        return detalle
    }
}
