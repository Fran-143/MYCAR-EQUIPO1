package com.mycar.app.features.alertas.domain

import com.mycar.app.features.alertas.data.ProgramarAlertaDto
import java.io.IOException

data class TipoAlerta(val codigo: String, val nombre: String)
data class ReferenciaMantenimientoAlerta(val id: Long, val codigoTipo: String, val fecha: String, val servicioOtro: String?)
data class ContextoProgramarAlerta(
    val fechaServidor: String, val kmActual: Int?, val idLecturaActual: Long?,
    val tipos: List<TipoAlerta>, val referencias: List<ReferenciaMantenimientoAlerta>, val disponible: Boolean
)
data class DatosAlerta(
    val id: Long, val tipo: String, val criterio: String, val estado: String,
    val fechaObjetivo: String?, val kmObjetivo: Int?, val servicioOtro: String?
)

data class EstadoOperacionAlerta(
    val estado: String,
    val idAlerta: Long?
)

interface AlertasRepository {
    suspend fun contexto(idVehiculo: Long): ContextoProgramarAlerta
    suspend fun listar(idVehiculo: Long): Pair<Int, List<DatosAlerta>>
    suspend fun programar(idVehiculo: Long, solicitud: ProgramarAlertaDto): Long
    suspend fun operacion(idOperacion: String): EstadoOperacionAlerta
}

/** Resultado HTTP 202 / operación aún no confirmada; el cliente debe consultar idOperacion. */
class ResultadoOperacionIncierto(
    val idOperacion: String,
    mensaje: String = "La operación sigue en proceso."
) : IOException(mensaje)
