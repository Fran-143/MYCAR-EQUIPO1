package com.mycar.app.features.vehiculos.data

import com.google.gson.Gson
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import com.mycar.app.features.vehiculos.domain.RegistroVehiculoRepository
import retrofit2.Response

class RemoteRegistroVehiculoRepository(private val api: RegistroVehiculoApi) : RegistroVehiculoRepository {
    private val gson = Gson()
    private data class ErrorApi(val codigo: String?, val campos: List<String>?)

    override suspend fun catalogos(): CatalogosRegistroDto {
        val c = cuerpo(api.catalogos())
        if (c.usos.isNullOrEmpty() || c.tiposMotor.isNullOrEmpty() ||
            c.combustibles.isNullOrEmpty() || c.tiposAceite.isNullOrEmpty())
            throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
        return c
    }
    override suspend fun cupo(): CupoRegistroDto {
        val c = cuerpo(api.cupo())
        if (c.plan != "FREE" || c.limite != 1 || c.registrados < 0 ||
            c.puedeRegistrar != (c.registrados < c.limite)) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
        return c
    }
    override suspend fun registrar(solicitud: SolicitudRegistroVehiculoDto) = cuerpo(api.registrar(solicitud))
    override suspend fun operacion(idOperacion: String) = cuerpo(api.operacion(idOperacion))

    private fun <T> cuerpo(respuesta: Response<T>): T {
        if (respuesta.isSuccessful) return respuesta.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
        val error = try {
            respuesta.errorBody()?.use { gson.fromJson(it.string(), ErrorApi::class.java) }
        } catch (_: Exception) { null }
        val codigo = error?.codigo ?: when (respuesta.code()) {
            401, 403 -> "SESION_NO_VALIDA"
            else -> "ERROR_HTTP"
        }
        throw FalloRegistroVehiculo(codigo, respuesta.code(), error?.campos?.toSet().orEmpty())
    }
}
