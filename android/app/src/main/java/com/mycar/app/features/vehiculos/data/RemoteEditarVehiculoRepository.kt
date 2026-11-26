package com.mycar.app.features.vehiculos.data

import com.google.gson.Gson
import com.mycar.app.features.vehiculos.domain.EditarVehiculoRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo

class RemoteEditarVehiculoRepository(
    private val detalleApi: DetalleVehiculoApi,
    private val registroApi: RegistroVehiculoApi
) : EditarVehiculoRepository {
    private data class ErrorApi(val codigo: String?, val mensaje: String?, val campos: List<String>? = null)

    override suspend fun catalogos(): CatalogosRegistroDto {
        val r = registroApi.catalogos()
        if (!r.isSuccessful) throw fallo(r.code(), r.errorBody()?.string())
        return r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    override suspend fun consultar(idVehiculo: Long): DetalleVehiculoDto {
        val r = detalleApi.consultar(idVehiculo)
        if (!r.isSuccessful) throw fallo(r.code(), r.errorBody()?.string())
        return r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    override suspend fun actualizar(idVehiculo: Long, solicitud: SolicitudActualizacionVehiculoDto): DetalleVehiculoDto {
        val r = detalleApi.actualizar(idVehiculo, solicitud)
        if (!r.isSuccessful) throw fallo(r.code(), r.errorBody()?.string())
        val body = r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        if (body.idVehiculo != idVehiculo || body.placa.isBlank()) {
            throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        }
        return body
    }

    private fun fallo(http: Int, cuerpo: String?): FalloRegistroVehiculo {
        val parsed = runCatching { Gson().fromJson(cuerpo, ErrorApi::class.java) }.getOrNull()
        return FalloRegistroVehiculo(
            parsed?.codigo ?: if (http == 401) "SESION_NO_VALIDA" else "ERROR_HTTP",
            http,
            parsed?.campos?.toSet().orEmpty(),
            parsed?.mensaje
        )
    }
}
