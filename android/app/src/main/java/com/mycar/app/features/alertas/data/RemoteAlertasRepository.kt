package com.mycar.app.features.alertas.data

import com.google.gson.Gson
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.ContextoProgramarAlerta
import com.mycar.app.features.alertas.domain.DatosAlerta
import com.mycar.app.features.alertas.domain.EstadoOperacionAlerta
import com.mycar.app.features.alertas.domain.ReferenciaMantenimientoAlerta
import com.mycar.app.features.alertas.domain.ResultadoOperacionIncierto
import com.mycar.app.features.alertas.domain.TipoAlerta
import java.io.IOException
import java.util.UUID
import retrofit2.HttpException

class RemoteAlertasRepository(private val api: AlertasApi) : AlertasRepository {
    private data class ErrorApi(val codigo: String?, val mensaje: String?, val resultadoIncierto: Boolean? = null)

    override suspend fun contexto(idVehiculo: Long): ContextoProgramarAlerta {
        try {
            val c = api.contexto(idVehiculo)
            return ContextoProgramarAlerta(
                c.fechaServidor.orEmpty(), c.kmActual, c.idLecturaActual,
                c.tipos.orEmpty().map { TipoAlerta(it.codigo.orEmpty(), it.nombre.orEmpty()) }
                    .filter { it.codigo.isNotBlank() },
                c.referencias.orEmpty().map {
                    ReferenciaMantenimientoAlerta(it.idMantenimiento ?: 0, it.codigoTipo.orEmpty(),
                        it.fechaServicio.orEmpty(), it.servicioOtro)
                },
                c.programacionDisponible == true
            )
        } catch (e: Exception) {
            throw IOException(mensajeHttp(e, "No se pudo cargar el contexto de alertas"), e)
        }
    }

    override suspend fun listar(idVehiculo: Long): Pair<Int, List<DatosAlerta>> {
        try {
            val r = api.listar(idVehiculo)
            return (r.alertasSincronizadas ?: 0) to r.alertas.orEmpty().map {
                DatosAlerta(it.idAlerta ?: 0, it.nombreTipo.orEmpty(), it.criterio.orEmpty(), it.estado.orEmpty(),
                    it.fechaObjetivo, it.kmObjetivo, it.servicioOtro)
            }
        } catch (e: Exception) {
            throw IOException(mensajeHttp(e, "No se pudieron cargar las alertas"), e)
        }
    }

    override suspend fun programar(idVehiculo: Long, solicitud: ProgramarAlertaDto): Long {
        val op = solicitud.idOperacion.ifBlank { UUID.randomUUID().toString() }
        val body = solicitud.copy(idOperacion = op)
        try {
            val resp = api.programar(idVehiculo, body)
            return resp.idAlerta ?: error("Sin idAlerta")
        } catch (e: Exception) {
            val http = e as? HttpException ?: (e.cause as? HttpException)
            val cuerpo = http?.let { leerError(it) }
            if (http?.code() == 202 || cuerpo?.codigo == "OPERACION_EN_PROCESO" || cuerpo?.resultadoIncierto == true) {
                throw ResultadoOperacionIncierto(op, cuerpo?.mensaje ?: "La operación sigue en proceso.")
            }
            throw IOException(mensajeHttp(e, "No se pudo programar la alerta"), e)
        }
    }

    override suspend fun operacion(idOperacion: String): EstadoOperacionAlerta {
        try {
            val r = api.operacion(idOperacion)
            return EstadoOperacionAlerta(r.estado.orEmpty(), r.resultado?.idAlerta)
        } catch (e: Exception) {
            throw IOException(mensajeHttp(e, "No se pudo consultar la operación"), e)
        }
    }

    private fun leerError(http: HttpException): ErrorApi? = runCatching {
        http.response()?.errorBody()?.use { Gson().fromJson(it.string(), ErrorApi::class.java) }
    }.getOrNull()

    private fun mensajeHttp(e: Exception, generico: String): String {
        val http = e as? HttpException ?: (e.cause as? HttpException)
        if (http != null) {
            val cuerpo = leerError(http)
            when (http.code()) {
                401 -> return cuerpo?.mensaje ?: "Inicia sesión para continuar."
                403 -> return cuerpo?.mensaje
                    ?: "Acceso denegado. Reinicia el backend con RF22 habilitado (script de integración local)."
                404 -> return cuerpo?.mensaje ?: "Vehículo o kilometraje no disponible para alertas."
                409 -> {
                    if (cuerpo?.codigo == "BASES_CAMBIARON") {
                        return cuerpo.mensaje ?: "Las bases cambiaron. Revisa el resumen."
                    }
                    return cuerpo?.mensaje ?: "Ya existe una alerta equivalente."
                }
                400 -> return cuerpo?.mensaje ?: "Revisa los datos de la alerta."
                202 -> return cuerpo?.mensaje ?: "La operación sigue en proceso."
            }
            return cuerpo?.mensaje ?: "$generico (HTTP ${http.code()})"
        }
        return e.message?.takeIf { it.isNotBlank() } ?: generico
    }
}
