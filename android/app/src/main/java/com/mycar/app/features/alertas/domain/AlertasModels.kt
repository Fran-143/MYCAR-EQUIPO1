package com.mycar.app.features.alertas.domain

import com.mycar.app.features.alertas.data.ProgramarAlertaDto
import java.io.IOException

data class TipoAlerta(val codigo: String, val nombre: String)
data class ReferenciaMantenimientoAlerta(
    val id: Long,
    val codigoTipo: String,
    val fecha: String,
    val kmServicio: Int?,
    val servicioOtro: String?
)
data class ContextoProgramarAlerta(
    val fechaServidor: String, val kmActual: Int?, val idLecturaActual: Long?,
    val tipos: List<TipoAlerta>, val referencias: List<ReferenciaMantenimientoAlerta>, val disponible: Boolean
)
data class DatosAlerta(
    val id: Long, val tipo: String, val criterio: String, val estado: String,
    val fechaObjetivo: String?, val kmObjetivo: Int?, val servicioOtro: String?
)

data class DetalleAlerta(
    val id: Long,
    val categoria: String,
    val codigoTipo: String,
    val tipo: String,
    val criterio: String,
    val origenCalculo: String,
    val estado: String,
    val fechaEstadoUtc: String?,
    val fechaObjetivo: String?,
    val kmObjetivo: Int?,
    val kmBase: Int?,
    val intervaloKm: Int?,
    val unidadTiempo: String?,
    val cantidadTiempo: Int?,
    val fechaBase: String?,
    val servicioOtro: String?,
    val idMantenimientoReferencia: Long?,
    val idMantenimientoAtencion: Long?,
    val versionFila: Int,
    val vehiculoEtiqueta: String,
    val puedeAtender: Boolean,
    val puedeAnular: Boolean
)

data class EstadoOperacionAlerta(
    val estado: String,
    val idAlerta: Long?
)

data class SolicitudAtenderAlerta(
    val fechaServicio: String,
    val kmServicio: Int,
    val costoTotal: String? = null,
    val observaciones: String? = null
)

interface AlertasRepository {
    suspend fun contexto(idVehiculo: Long): ContextoProgramarAlerta
    suspend fun listar(idVehiculo: Long): Pair<Int, List<DatosAlerta>>
    suspend fun detalle(idVehiculo: Long, idAlerta: Long): DetalleAlerta
    suspend fun programar(idVehiculo: Long, solicitud: ProgramarAlertaDto): Long
    suspend fun operacion(idOperacion: String): EstadoOperacionAlerta
    suspend fun atender(idVehiculo: Long, idAlerta: Long, solicitud: SolicitudAtenderAlerta): Long
    suspend fun anular(idVehiculo: Long, idAlerta: Long)
}

/** Resultado HTTP 202 / operación aún no confirmada; el cliente debe consultar idOperacion. */
class ResultadoOperacionIncierto(
    val idOperacion: String,
    mensaje: String = "La operación sigue en proceso."
) : IOException(mensaje)
