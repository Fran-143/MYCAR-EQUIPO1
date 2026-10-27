package com.mycar.app.features.mantenimiento.data

import com.google.gson.Gson
import com.mycar.app.features.mantenimiento.domain.*
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime

/** Reutiliza MantenimientoApi creado por el cliente Retrofit compartido. No crea otro cliente. */
class RemoteMantenimientoRepository(private val api: MantenimientoApi) : MantenimientoRepository {
    private val gson = Gson()
    override suspend fun contexto(): ContextoRf17Dto {
        val c = respuesta(false) { api.contexto() }
        try {
            require(c.zonaHoraria == "America/Lima")
            LocalDate.parse(c.fechaServidor)
            c.vehiculos.forEach { v ->
                require(v.idVehiculo > 0 && v.placa.isNotBlank())
                if (v.idLecturaActual != null) {
                    require(v.idLecturaActual > 0 && v.kmActual?.let { it > 0 } == true)
                    OffsetDateTime.parse(v.fechaLecturaActual)
                }
            }
            c.tipos.forEach { require(it.idTipoMantenimiento > 0 && it.codigo.isNotBlank() && it.nombre.isNotBlank()) }
        } catch (e: Exception) {
            throw ErrorMantenimientoRemoto("RESPUESTA_INVALIDA", "No se pudieron cargar referencias válidas")
        }
        return c
    }
    override suspend fun lecturas(vehiculo: Long, fecha: String): List<LecturaRf17Dto> {
        val lecturas = respuesta(false) { api.lecturas(vehiculo, fecha) }
        try {
            lecturas.forEach {
                require(it.idLecturaKm > 0 && it.valorKm > 0)
                OffsetDateTime.parse(it.fechaLecturaUtc)
            }
        } catch (e: Exception) {
            throw ErrorMantenimientoRemoto("RESPUESTA_INVALIDA", "No se pudieron cargar lecturas válidas")
        }
        return lecturas
    }
    override suspend fun registrar(vehiculo: Long, solicitud: SolicitudRf17Dto) =
        respuesta(true) { api.registrar(vehiculo, solicitud) }.also { verificar(it, solicitud) }
    override suspend fun consultar(idOperacion: String) = respuesta(false) { api.operacion(idOperacion) }.also {
        if (it.idOperacion != idOperacion || it.estado !in listOf("CONFIRMADA", "EN_PROCESO", "NO_ENCONTRADA"))
            throw ErrorMantenimientoRemoto("RESPUESTA_INVALIDA", "No se pudo verificar la operación", true)
    }
    fun verificar(r: ResultadoRf17Dto, p: SolicitudRf17Dto) {
        val costoIgual = if (p.costoTotal == null) r.costoTotal == null
            else r.costoTotal?.compareTo(p.costoTotal) == 0
        if (r.idOperacion != p.idOperacion || r.idMantenimiento?.let { it > 0 } != true
            || r.estado != "REALIZADO" || r.kmServicio != p.kmServicio || r.fechaServicio != p.fechaServicio
            || r.cantidadRepuestos != p.repuestos.size || r.alertaCreada != false || !costoIgual) {
            throw ErrorMantenimientoRemoto("RESPUESTA_INVALIDA", "No se pudo confirmar el resultado del guardado", true)
        }
    }
    private suspend fun <T : Any> respuesta(escritura: Boolean, bloque: suspend () -> Response<T>): T {
        try {
            val r = bloque()
            if (r.isSuccessful) return r.body()
                ?: throw ErrorMantenimientoRemoto("RESPUESTA_VACIA", "No se recibió una respuesta válida", escritura)
            val error = runCatching { gson.fromJson(r.errorBody()?.string(), ErrorRf17Dto::class.java) }.getOrNull()
            val incierto = escritura && (error?.resultadoIncierto == true ||
                    (r.code() >= 500 && error?.codigo != "INTEGRACION_PENDIENTE") ||
                    error?.codigo == "OPERACION_EN_PROCESO")
            val mensaje = error?.mensaje ?: when (r.code()) {
                401,403 -> "Inicia sesión nuevamente en MyCar"
                404 -> "El recurso solicitado no está disponible"
                else -> "No se pudo completar la solicitud"
            }
            throw ErrorMantenimientoRemoto(error?.codigo ?: "HTTP_${r.code()}", mensaje, incierto,
                error?.campos ?: emptyMap())
        } catch (e: CancellationException) { throw e
        } catch (e: ErrorMantenimientoRemoto) { throw e
        } catch (e: IOException) {
            throw ErrorMantenimientoRemoto("SIN_CONEXION", if (escritura)
                "Se perdió la conexión. Verifica el resultado antes de volver a guardar"
                else "No se pudo conectar con MyCar", escritura)
        } catch (e: Exception) {
            throw ErrorMantenimientoRemoto("RESPUESTA_INVALIDA", "No se pudo interpretar la respuesta", escritura)
        }
    }
}
