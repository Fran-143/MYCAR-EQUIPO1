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
    val fechaObjetivo: String?, val kmObjetivo: Int?, val servicioOtro: String?,
    val codigoTipo: String = ""
)

data class ContactoTallerAlerta(
    val idTaller: Long?,
    val nombre: String?,
    val telefono: String?,
    val tieneWhatsapp: Boolean,
    /** Autorización explícita de contacto; Agente 2 enciende botones solo si true + asociado activo. */
    val contactoAutorizado: Boolean = false,
    val origen: String?,
    val origenSugerencia: String,
    val sinContactoDisponible: Boolean
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
    val puedeAnular: Boolean,
    val contactoTaller: ContactoTallerAlerta? = null
)

data class EstadoOperacionAlerta(
    val estado: String,
    val idAlerta: Long?
)

data class RepuestoAtencionAlerta(
    val nombre: String,
    val marca: String? = null,
    val cantidad: Int,
    val precioUnitario: String? = null
)

/** Payload RF24 alineado con RF17 (sin próxima fecha/km ni oferta RF22). */
data class SolicitudAtenderAlerta(
    val fechaServicio: String,
    val kmServicio: Int,
    val costoTotal: String? = null,
    val observaciones: String? = null,
    val idOperacion: String = "",
    val origenKmServicio: String = "HISTORICO_MANUAL",
    val idLecturaKmReferencia: Long? = null,
    val repuestos: List<RepuestoAtencionAlerta> = emptyList(),
    val idTaller: Long? = null
)

interface AlertasRepository {
    suspend fun contexto(
        idVehiculo: Long,
        codigoTipo: String? = null,
        servicioOtro: String? = null
    ): ContextoProgramarAlerta

    suspend fun listar(idVehiculo: Long): Pair<Int, List<DatosAlerta>>
    suspend fun detalle(idVehiculo: Long, idAlerta: Long): DetalleAlerta
    suspend fun programar(idVehiculo: Long, solicitud: ProgramarAlertaDto): Long
    suspend fun operacion(idOperacion: String): EstadoOperacionAlerta
    suspend fun atender(idVehiculo: Long, idAlerta: Long, solicitud: SolicitudAtenderAlerta): Long
    suspend fun anular(idVehiculo: Long, idAlerta: Long)
    suspend fun anular(idVehiculo: Long, idAlerta: Long, motivo: String, idOperacion: String)
    suspend fun elegirContactoTaller(idVehiculo: Long, idAlerta: Long, idTaller: Long): DetalleAlerta
}

data class SolicitudAnularAlerta(
    val motivo: String,
    val idOperacion: String = ""
)

/** Resultado HTTP 202 / operación aún no confirmada; el cliente debe consultar idOperacion. */
class ResultadoOperacionIncierto(
    val idOperacion: String,
    mensaje: String = "La operación sigue en proceso."
) : IOException(mensaje)
