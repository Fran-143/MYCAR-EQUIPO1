package com.mycar.app.features.vehiculos.data

import com.google.gson.Gson
import com.mycar.app.features.vehiculos.domain.ActualizarKilometrajeRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import java.util.UUID

class RemoteActualizarKilometrajeRepository(private val api: KilometrajeApi) : ActualizarKilometrajeRepository {
    private data class ErrorApi(val codigo: String?)
    override suspend fun contexto(idVehiculo: Long): ContextoKilometrajeDto =
        exitoso(api.contexto(idVehiculo), idVehiculo) { it.idVehiculo == idVehiculo && it.kmActual > 0 && it.idLectura > 0 }

    override suspend fun registrar(idVehiculo: Long, solicitud: SolicitudActualizarKmDto): ResultadoActualizarKmDto =
        exitoso(api.registrar(idVehiculo, solicitud), idVehiculo) {
            it.idVehiculo == idVehiculo && it.kmActual == solicitud.kmNuevo && it.estadoOperacion == "CONFIRMADA"
        }

    override suspend fun operacion(idOperacion: UUID): EstadoOperacionKmDto {
        val r = api.operacion(idOperacion)
        if (!r.isSuccessful) throw fallo(r)
        return r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
    }

    private inline fun <T> exitoso(r: retrofit2.Response<T>, idVehiculo: Long, validar: (T) -> Boolean): T {
        if (!r.isSuccessful) throw fallo(r)
        val cuerpo = r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
        if (!validar(cuerpo)) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
        return cuerpo
    }

    private fun fallo(r: retrofit2.Response<*>): FalloRegistroVehiculo {
        val codigo = runCatching { r.errorBody()?.use { Gson().fromJson(it.string(), ErrorApi::class.java)?.codigo } }.getOrNull()
        return FalloRegistroVehiculo(codigo ?: if (r.code() == 401) "SESION_NO_VALIDA" else "ERROR_HTTP", r.code())
    }
}
