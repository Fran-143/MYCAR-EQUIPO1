package com.mycar.app.features.alertas.data

import com.google.gson.Gson
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.ContactoTallerAlerta
import com.mycar.app.features.alertas.domain.ContextoProgramarAlerta
import com.mycar.app.features.alertas.domain.DatosAlerta
import com.mycar.app.features.alertas.domain.DetalleAlerta
import com.mycar.app.features.alertas.domain.EstadoOperacionAlerta
import com.mycar.app.features.alertas.domain.ReferenciaMantenimientoAlerta
import com.mycar.app.features.alertas.domain.ResultadoOperacionIncierto
import com.mycar.app.features.alertas.domain.SolicitudAtenderAlerta
import com.mycar.app.features.alertas.domain.TipoAlerta
import java.io.IOException
import java.util.UUID
import retrofit2.HttpException

class RemoteAlertasRepository(private val api: AlertasApi) : AlertasRepository {
    private data class ErrorApi(val codigo: String?, val mensaje: String?, val resultadoIncierto: Boolean? = null)

    override suspend fun contexto(
        idVehiculo: Long,
        codigoTipo: String?,
        servicioOtro: String?
    ): ContextoProgramarAlerta {
        try {
            val tipo = codigoTipo?.takeIf { it.isNotBlank() }
            val otro = if (tipo == "OTRO") servicioOtro?.takeIf { it.isNotBlank() } else null
            val c = api.contexto(idVehiculo, tipo, otro)
            return ContextoProgramarAlerta(
                c.fechaServidor.orEmpty(), c.kmActual, c.idLecturaActual,
                c.tipos.orEmpty().map { TipoAlerta(it.codigo.orEmpty(), it.nombre.orEmpty()) }
                    .filter { it.codigo.isNotBlank() },
                c.referencias.orEmpty().mapNotNull { r ->
                    val id = r.idMantenimiento?.takeIf { it > 0 } ?: return@mapNotNull null
                    val codigo = r.codigoTipo?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val fecha = r.fechaServicio?.take(10)?.takeIf { it.length == 10 } ?: return@mapNotNull null
                    ReferenciaMantenimientoAlerta(
                        id, codigo, fecha, r.kmServicio, r.servicioOtro?.takeIf { it.isNotBlank() }
                    )
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
                DatosAlerta(
                    it.idAlerta ?: 0, it.nombreTipo.orEmpty(), it.criterio.orEmpty(), it.estado.orEmpty(),
                    it.fechaObjetivo, it.kmObjetivo, it.servicioOtro, it.codigoTipo.orEmpty()
                )
            }
        } catch (e: Exception) {
            throw IOException(mensajeHttp(e, "No se pudieron cargar las alertas"), e)
        }
    }

    override suspend fun detalle(idVehiculo: Long, idAlerta: Long): DetalleAlerta {
        try {
            val d = api.detalle(idVehiculo, idAlerta)
            val id = d.idAlerta ?: throw IOException("Detalle de alerta incompleto")
            val v = d.vehiculo
            val etiqueta = listOfNotNull(v?.marca, v?.modelo).joinToString(" ").trim()
                .ifBlank { "Vehículo" } + (v?.placa?.let { " · $it" } ?: "")
            return mapDetalle(d, etiqueta)
        } catch (e: Exception) {
            throw IOException(mensajeHttp(e, "No se pudo cargar el detalle de la alerta"), e)
        }
    }

    override suspend fun elegirContactoTaller(idVehiculo: Long, idAlerta: Long, idTaller: Long): DetalleAlerta {
        try {
            val d = api.elegirContactoTaller(idVehiculo, idAlerta, ElegirContactoTallerDto(idTaller))
            val v = d.vehiculo
            val etiqueta = listOfNotNull(v?.marca, v?.modelo).joinToString(" ").trim()
                .ifBlank { "Vehículo" } + (v?.placa?.let { " · $it" } ?: "")
            return mapDetalle(d, etiqueta)
        } catch (e: Exception) {
            throw IOException(mensajeHttp(e, "No se pudo actualizar el taller de contacto"), e)
        }
    }

    private fun mapDetalle(d: DetalleAlertaDto, etiqueta: String): DetalleAlerta {
        val id = d.idAlerta ?: throw IOException("Detalle de alerta incompleto")
        val c = d.contactoTaller
        return DetalleAlerta(
            id = id,
            categoria = d.categoria.orEmpty().ifBlank { "MANTENIMIENTO" },
            codigoTipo = d.codigoTipo.orEmpty(),
            tipo = d.nombreTipo.orEmpty(),
            criterio = d.criterio.orEmpty(),
            origenCalculo = d.origenCalculo.orEmpty(),
            estado = d.estado.orEmpty(),
            fechaEstadoUtc = d.fechaEstadoUtc,
            fechaObjetivo = d.fechaObjetivo,
            kmObjetivo = d.kmObjetivo,
            kmBase = d.kmBase,
            intervaloKm = d.intervaloKm,
            unidadTiempo = d.unidadTiempo,
            cantidadTiempo = d.cantidadTiempo,
            fechaBase = d.fechaBase,
            servicioOtro = d.servicioOtro,
            idMantenimientoReferencia = d.idMantenimientoReferencia,
            idMantenimientoAtencion = d.idMantenimientoAtencion,
            versionFila = d.versionFila ?: 0,
            vehiculoEtiqueta = etiqueta,
            puedeAtender = d.acciones?.atender == true,
            puedeAnular = d.acciones?.anular == true,
            contactoTaller = c?.let {
                ContactoTallerAlerta(
                    idTaller = it.idTaller,
                    nombre = it.nombre,
                    telefono = it.telefono,
                    tieneWhatsapp = it.tieneWhatsapp == true,
                    contactoAutorizado = it.contactoAutorizado == true,
                    origen = it.origen,
                    origenSugerencia = it.origenSugerencia.orEmpty().ifBlank { "NINGUNO" },
                    sinContactoDisponible = it.sinContactoDisponible == true
                )
            }
        )
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

    override suspend fun atender(idVehiculo: Long, idAlerta: Long, solicitud: SolicitudAtenderAlerta): Long {
        try {
            val op = solicitud.idOperacion.ifBlank { UUID.randomUUID().toString() }
            val r = api.atender(
                idVehiculo, idAlerta,
                AtenderAlertaDto(
                    idOperacion = op,
                    fechaServicio = solicitud.fechaServicio,
                    kmServicio = solicitud.kmServicio,
                    origenKmServicio = solicitud.origenKmServicio,
                    idLecturaKmReferencia = solicitud.idLecturaKmReferencia,
                    costoTotal = solicitud.costoTotal,
                    observaciones = solicitud.observaciones,
                    repuestos = solicitud.repuestos.map {
                        AtenderRepuestoDto(it.nombre, it.marca, it.cantidad, it.precioUnitario)
                    }.ifEmpty { null },
                    idTaller = solicitud.idTaller
                )
            )
            return r.idMantenimientoAtencion ?: error("Sin idMantenimientoAtencion")
        } catch (e: Exception) {
            throw IOException(mensajeHttp(e, "No se pudo atender la alerta"), e)
        }
    }

    override suspend fun anular(idVehiculo: Long, idAlerta: Long) {
        anular(idVehiculo, idAlerta, "", "")
    }

    override suspend fun anular(idVehiculo: Long, idAlerta: Long, motivo: String, idOperacion: String) {
        try {
            val op = idOperacion.ifBlank { UUID.randomUUID().toString() }
            api.anular(idVehiculo, idAlerta, AnularAlertaDto(idOperacion = op, motivo = motivo.trim()))
        } catch (e: Exception) {
            throw IOException(mensajeHttp(e, "No se pudo anular la alerta"), e)
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
