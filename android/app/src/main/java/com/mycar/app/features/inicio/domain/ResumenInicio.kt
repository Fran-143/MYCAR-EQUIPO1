package com.mycar.app.features.inicio.domain

import com.mycar.app.features.historial.domain.MantenimientoHistorial
import com.mycar.app.features.vehiculos.data.VehiculoResumenDto
import java.time.Instant

enum class FaseInicio { SIN_SESION, CARGANDO, SIN_VEHICULOS, LISTO, ERROR, INCONSISTENTE }
enum class DisponibilidadInicio { CARGANDO, DISPONIBLE, NO_DISPONIBLE }
data class BloquesInicio(
    val kilometraje: Int?, val fechaLectura: Instant? = null,
    val ultimoMantenimiento: MantenimientoHistorial? = null,
    val historial: DisponibilidadInicio = DisponibilidadInicio.CARGANDO,
    val lectura: DisponibilidadInicio = DisponibilidadInicio.CARGANDO
)
data class EstadoResumenInicio(
    val fase: FaseInicio = FaseInicio.SIN_SESION,
    val vehiculos: List<VehiculoResumenDto> = emptyList(),
    val seleccionado: Long? = null, val bloques: BloquesInicio? = null,
    val mensaje: String? = null, val nombreUsuario: String? = null
) {
    val vehiculo: VehiculoResumenDto? get() = vehiculos.firstOrNull { it.idVehiculo == seleccionado }
}
interface ResumenInicioRepository {
    suspend fun vehiculosActivos(): List<VehiculoResumenDto>
    suspend fun bloques(vehiculo: VehiculoResumenDto): BloquesInicio
}
class ErrorResumenInicio(val sesion: Boolean = false, val inconsistente: Boolean = false) : Exception()

/** RF50: una selección de Inicio jamás cambia el principal ni persiste cambios. */
object ReglasResumenInicio {
    fun validarYSeleccionar(vehiculos: List<VehiculoResumenDto>, anterior: Long?): Long? {
        if (vehiculos.isEmpty()) return null
        if (vehiculos.any { it.estadoVehiculo != "ACTIVO" || it.idVehiculo <= 0 } ||
            vehiculos.map { it.idVehiculo }.distinct().size != vehiculos.size ||
            vehiculos.count { it.principal } != 1) throw ErrorResumenInicio(inconsistente=true)
        return anterior?.takeIf { id -> vehiculos.any { it.idVehiculo == id } }
            ?: vehiculos.single { it.principal }.idVehiculo
    }
    fun ultimoRealizado(lista: List<MantenimientoHistorial>): MantenimientoHistorial? = lista
        .filter { it.estado == "REALIZADO" }
        .maxWithOrNull(compareBy<MantenimientoHistorial> { it.fecha }.thenBy { it.id })
}
