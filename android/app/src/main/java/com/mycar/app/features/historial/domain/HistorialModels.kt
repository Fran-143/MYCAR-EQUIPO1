package com.mycar.app.features.historial.domain

import java.math.BigDecimal
import java.time.LocalDate

data class VehiculoHistorial(
    val id: Long, val placa: String, val marca: String, val modelo: String,
    val estado: String, val principal: Boolean
) {
    val activo: Boolean get() = estado == "ACTIVO"
    val nombre: String get() = "$marca $modelo"
}

data class MantenimientoHistorial(
    val id: Long, val codigoTipo: String, val tipo: String, val servicioOtro: String?,
    val fecha: LocalDate, val kmServicio: Int, val estado: String, val costo: BigDecimal?,
    val taller: String? = null
) {
    val titulo: String get() = if (codigoTipo == "OTRO") servicioOtro.orEmpty() else tipo
}

data class ResultadoHistorial(val vehiculo: VehiculoHistorial, val mantenimientos: List<MantenimientoHistorial>)

data class RepuestoHistorial(
    val nombre: String, val marca: String?, val cantidad: Int, val precioUnitario: BigDecimal?
)

/** RF19 detalle (+ flags RF20/RF21). */
data class DetalleMantenimientoHistorial(
    val id: Long,
    val idVehiculo: Long,
    val placa: String? = null,
    val marca: String? = null,
    val modelo: String? = null,
    val estadoVehiculo: String? = null,
    val codigoTipo: String,
    val tipo: String,
    val servicioOtro: String?,
    val fecha: LocalDate,
    val kmServicio: Int,
    val estado: String,
    val costo: BigDecimal?,
    val observaciones: String?,
    val taller: String?,
    val idTaller: Long? = null,
    val proximaFechaSugerida: LocalDate? = null,
    val proximoKmSugerido: Int? = null,
    val anuladoUtc: String? = null,
    val editable: Boolean,
    val anulable: Boolean,
    val bloqueadoPorAlertaActiva: Boolean,
    val repuestos: List<RepuestoHistorial> = emptyList()
) {
    val titulo: String get() = if (codigoTipo == "OTRO") servicioOtro.orEmpty() else tipo
    val etiquetaVehiculo: String
        get() = listOfNotNull(marca, modelo).joinToString(" ").ifBlank { "Vehículo" } +
            (placa?.let { " · $it" } ?: "")
    val tieneProximo: Boolean get() = proximaFechaSugerida != null || (proximoKmSugerido != null && proximoKmSugerido > 0)
}

data class ModificarMantenimientoDatos(
    val costoTotal: BigDecimal?,
    val observaciones: String?,
    val idTaller: Long? = null,
    val sinTaller: Boolean = false
)

interface HistorialRepository {
    suspend fun vehiculos(): List<VehiculoHistorial>
    suspend fun historial(idVehiculo: Long): ResultadoHistorial
    suspend fun detalle(idVehiculo: Long, idMantenimiento: Long): DetalleMantenimientoHistorial
    suspend fun modificar(idVehiculo: Long, idMantenimiento: Long, datos: ModificarMantenimientoDatos): DetalleMantenimientoHistorial
    suspend fun anular(idVehiculo: Long, idMantenimiento: Long)
}

enum class CodigoErrorHistorial {
    SESION, VEHICULO_NO_DISPONIBLE, MANTENIMIENTO_NO_DISPONIBLE, ALERTA_ACTIVA, CONEXION, INTEGRACION, DATOS
}
class ErrorHistorial(
    val codigo: CodigoErrorHistorial,
    override val message: String,
    val idAlerta: Long? = null
) : Exception(message)

enum class FaseHistorial {
    SESION_REQUERIDA, CARGANDO_VEHICULOS, SIN_VEHICULOS, SIN_SELECCION,
    CARGANDO_HISTORIAL, LISTO, ERROR_VEHICULOS, ERROR_HISTORIAL
}

enum class FaseKilometrajeHistorial { IDLE, CARGANDO, LISTO, VACIO, ERROR }

/** Lectura histórica RF15 (solo consulta). */
data class LecturaKilometrajeHistorial(
    val id: Long,
    val km: Double,
    val origen: String,
    val fechaUtc: String?,
    val kmAnteriorCorreccion: Double? = null,
    val motivoCorreccion: String? = null,
    val fechaRectificacionUtc: String? = null
)

data class EstadoHistorial(
    val fase: FaseHistorial = FaseHistorial.SESION_REQUERIDA,
    val vehiculos: List<VehiculoHistorial> = emptyList(),
    val seleccionado: VehiculoHistorial? = null,
    /** Lista completa desde API (orden servidor DESC). */
    val mantenimientos: List<MantenimientoHistorial> = emptyList(),
    /** null = Todos los tipos. */
    val filtroCodigoTipo: String? = null,
    val ordenRecientes: Boolean = true,
    val mensaje: String? = null,
    val aviso: String? = null,
    val faseKm: FaseKilometrajeHistorial = FaseKilometrajeHistorial.IDLE,
    val lecturasKm: List<LecturaKilometrajeHistorial> = emptyList(),
    val mensajeKm: String? = null
) {
    val visibles: List<MantenimientoHistorial>
        get() = FiltroHistorialMantenimientos.aplicar(mantenimientos, filtroCodigoTipo, ordenRecientes)

    val tiposFiltro: List<Pair<String, String>>
        get() = FiltroHistorialMantenimientos.tiposPresentes(mantenimientos)

    val talleresDerivados: List<String>
        get() = mantenimientos.mapNotNull { it.taller?.trim()?.takeIf { n -> n.isNotEmpty() } }
            .distinct()
            .sorted()

    val contador: Int? get() = if (fase == FaseHistorial.LISTO) visibles.size else null
    val filtrosActivos: Boolean get() = filtroCodigoTipo != null || !ordenRecientes
    val sinCoincidenciasFiltro: Boolean
        get() = fase == FaseHistorial.LISTO && mantenimientos.isNotEmpty() && visibles.isEmpty()
    val puedeRegistrar: Boolean get() = fase == FaseHistorial.LISTO && seleccionado?.activo == true
    val cargando: Boolean get() = fase == FaseHistorial.CARGANDO_VEHICULOS || fase == FaseHistorial.CARGANDO_HISTORIAL
}

object FiltroHistorialMantenimientos {
    fun tiposPresentes(lista: List<MantenimientoHistorial>): List<Pair<String, String>> =
        lista.groupBy { it.codigoTipo }
            .map { (codigo, regs) -> codigo to (regs.firstOrNull()?.tipo ?: codigo) }
            .sortedBy { it.second.lowercase() }

    fun aplicar(
        lista: List<MantenimientoHistorial>,
        codigoTipo: String?,
        ordenRecientes: Boolean
    ): List<MantenimientoHistorial> {
        val filtrada = if (codigoTipo.isNullOrBlank()) lista
        else lista.filter { it.codigoTipo == codigoTipo }
        return if (ordenRecientes) {
            filtrada.sortedWith(compareByDescending<MantenimientoHistorial> { it.fecha }.thenByDescending { it.id })
        } else {
            filtrada.sortedWith(compareBy<MantenimientoHistorial> { it.fecha }.thenBy { it.id })
        }
    }
}
