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

interface HistorialRepository {
    suspend fun vehiculos(): List<VehiculoHistorial>
    suspend fun historial(idVehiculo: Long): ResultadoHistorial
}

enum class CodigoErrorHistorial { SESION, VEHICULO_NO_DISPONIBLE, CONEXION, INTEGRACION, DATOS }
class ErrorHistorial(val codigo: CodigoErrorHistorial, override val message: String) : Exception(message)

enum class FaseHistorial {
    SESION_REQUERIDA, CARGANDO_VEHICULOS, SIN_VEHICULOS, SIN_SELECCION,
    CARGANDO_HISTORIAL, LISTO, ERROR_VEHICULOS, ERROR_HISTORIAL
}

data class EstadoHistorial(
    val fase: FaseHistorial = FaseHistorial.SESION_REQUERIDA,
    val vehiculos: List<VehiculoHistorial> = emptyList(),
    val seleccionado: VehiculoHistorial? = null,
    val mantenimientos: List<MantenimientoHistorial> = emptyList(),
    val mensaje: String? = null,
    val aviso: String? = null
) {
    val contador: Int? get() = if (fase == FaseHistorial.LISTO) mantenimientos.size else null
    val puedeRegistrar: Boolean get() = fase == FaseHistorial.LISTO && seleccionado?.activo == true
    val cargando: Boolean get() = fase == FaseHistorial.CARGANDO_VEHICULOS || fase == FaseHistorial.CARGANDO_HISTORIAL
}
