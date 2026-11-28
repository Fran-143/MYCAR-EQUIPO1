package com.mycar.app.features.vehiculos.data

import com.google.gson.Gson
import com.mycar.app.features.vehiculos.domain.ActualizarKilometrajeRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import java.util.UUID

class RemoteActualizarKilometrajeRepository(private val api: KilometrajeApi) : ActualizarKilometrajeRepository {
    private data class ErrorApi(val codigo: String?, val mensaje: String? = null)
    override suspend fun contexto(idVehiculo: Long): ContextoKilometrajeDto =
        exitoso(api.contexto(idVehiculo), idVehiculo) { it.idVehiculo == idVehiculo && it.kmActual > 0 && it.idLectura > 0 }

    override suspend fun listarLecturas(idVehiculo: Long): List<LecturaKilometrajeDto> {
        val r = api.listarLecturas(idVehiculo)
        if (!r.isSuccessful) throw fallo(r)
        return r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
    }

    override suspend fun registrar(idVehiculo: Long, solicitud: SolicitudActualizarKmDto): ResultadoActualizarKmDto =
        exitoso(api.registrar(idVehiculo, solicitud), idVehiculo) {
            it.idVehiculo == idVehiculo && it.kmActual == solicitud.kmNuevo && it.estadoOperacion == "CONFIRMADA"
        }

    override suspend fun operacion(idOperacion: UUID): EstadoOperacionKmDto {
        val r = api.operacion(idOperacion)
        if (!r.isSuccessful) throw fallo(r)
        return r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
    }

    override suspend fun contextoCorreccion(idVehiculo: Long, idLectura: Long): ContextoCorreccionKmDto =
        exitoso(api.contextoCorreccion(idVehiculo, idLectura), idVehiculo) {
            it.idVehiculo == idVehiculo && it.idLectura == idLectura
        }

    override suspend fun corregir(
        idVehiculo: Long,
        idLectura: Long,
        solicitud: SolicitudCorregirKmDto
    ): ResultadoCorregirKmDto =
        exitoso(api.corregir(idVehiculo, idLectura, solicitud), idVehiculo) {
            it.idVehiculo == idVehiculo && it.idLectura == idLectura && it.estadoOperacion == "CONFIRMADA"
        }

    override suspend fun operacionCorreccion(idOperacion: UUID): EstadoOperacionCorreccionKmDto {
        val r = api.operacionCorreccion(idOperacion)
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
        val err = runCatching {
            r.errorBody()?.use { Gson().fromJson(it.string(), ErrorApi::class.java) }
        }.getOrNull()
        val codigo = err?.codigo ?: if (r.code() == 401) "SESION_NO_VALIDA" else "ERROR_HTTP"
        return FalloRegistroVehiculo(codigo, r.code(), mensajeServidor = err?.mensaje)
    }
}
