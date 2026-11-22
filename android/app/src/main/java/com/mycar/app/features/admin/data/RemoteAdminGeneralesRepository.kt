package com.mycar.app.features.admin.data

import com.google.gson.Gson
import com.mycar.app.features.admin.domain.AdminGeneralesRepository
import com.mycar.app.features.soporte.data.ContadoresIncidenciaDto
import com.mycar.app.features.soporte.data.SolicitudDetalleDto
import com.mycar.app.features.soporte.data.SolicitudResumenDto
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RemoteAdminGeneralesRepository(private val api: AdminGeneralesApi) : AdminGeneralesRepository {
    private data class ErrorApi(val codigo: String?, val mensaje: String? = null)

    override suspend fun listar(estado: String?, q: String?): List<SolicitudResumenDto> {
        val r = api.listar(estado, q)
        if (!r.isSuccessful) throw fallo(r)
        return r.body().orEmpty()
    }

    override suspend fun contadores(): ContadoresIncidenciaDto {
        val r = api.contadores()
        if (!r.isSuccessful) throw fallo(r)
        return r.body() ?: ContadoresIncidenciaDto()
    }

    override suspend fun detalle(codigo: String): SolicitudDetalleDto {
        val r = api.detalle(codigo)
        if (!r.isSuccessful) throw fallo(r)
        return r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    override suspend fun aceptar(codigo: String, observacion: String?) =
        // Siempre enviar JSON (nunca body null): Spring falla si el POST llega sin cuerpo.
        accion(api.aceptar(codigo, AdminObservacionBody(observacion?.trim()?.ifBlank { null })))

    override suspend fun iniciarAtencion(codigo: String, observacion: String?) =
        accion(api.iniciarAtencion(codigo, AdminObservacionBody(observacion?.trim()?.ifBlank { null })))

    override suspend fun terminar(codigo: String, respuesta: String) =
        accion(api.terminar(codigo, AdminTerminarBody(respuesta)))

    override suspend fun rechazar(codigo: String, motivo: String) =
        accion(api.rechazar(codigo, AdminRechazarBody(motivo)))

    override suspend fun descargarEvidencia(codigo: String): ByteArray = withContext(Dispatchers.IO) {
        val r = api.evidencia(codigo)
        if (!r.isSuccessful) throw fallo(r)
        r.body()?.bytes() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    private fun accion(r: retrofit2.Response<AdminAccionResponse>): AdminAccionResponse {
        if (!r.isSuccessful) throw fallo(r)
        return r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    private fun fallo(r: retrofit2.Response<*>): FalloRegistroVehiculo {
        val parsed = runCatching {
            r.errorBody()?.use { Gson().fromJson(it.string(), ErrorApi::class.java) }
        }.getOrNull()
        return FalloRegistroVehiculo(
            codigo = parsed?.codigo ?: if (r.code() == 401) "SESION_NO_VALIDA" else "ERROR_HTTP",
            estadoHttp = r.code(),
            mensajeServidor = parsed?.mensaje
        )
    }
}