package com.mycar.app.features.vehiculos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.vehiculos.data.DetalleVehiculoDto
import com.mycar.app.features.vehiculos.domain.DetalleVehiculosRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FaseDetalle { CARGANDO, LISTO, ERROR, SESION_REQUERIDA, NO_DISPONIBLE }

data class EstadoDetalleVehiculo(
    val idVehiculo: Long,
    val fase: FaseDetalle = FaseDetalle.CARGANDO,
    val detalle: DetalleVehiculoDto? = null,
    val mensaje: String? = null,
    val ocupado: Boolean = false,
    val mensajeAccion: String? = null,
    val accionError: Boolean = false
)

class DetalleVehiculoViewModel(
    private val idVehiculo: Long,
    private val repository: DetalleVehiculosRepository
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDetalleVehiculo(idVehiculo))
    val estado = _estado.asStateFlow()
    private var trabajo: Job? = null
    private var revision = 0L

    init { cargar() }

    fun cargar() {
        val actual = ++revision
        trabajo?.cancel()
        _estado.value = _estado.value.copy(
            fase = FaseDetalle.CARGANDO,
            detalle = null,
            mensaje = null,
            ocupado = false,
            mensajeAccion = null,
            accionError = false
        )
        trabajo = viewModelScope.launch {
            try {
                val detalle = repository.consultar(idVehiculo)
                if (actual == revision) {
                    _estado.value = _estado.value.copy(fase = FaseDetalle.LISTO, detalle = detalle)
                }
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (error: Exception) {
                if (actual != revision) return@launch
                aplicarErrorCarga(error)
            }
        }
    }

    fun establecerPrincipal(onExito: () -> Unit = {}) {
        ejecutarAccion(
            exitoMensaje = "Vehículo principal actualizado.",
            onExito = onExito
        ) { repository.establecerPrincipal(idVehiculo) }
    }

    fun activar(onExito: () -> Unit = {}) {
        ejecutarAccion(
            exitoMensaje = "Vehículo activado.",
            onExito = onExito
        ) { repository.cambiarEstado(idVehiculo, "ACTIVO") }
    }

    fun desactivar(onExito: () -> Unit = {}) {
        ejecutarAccion(
            exitoMensaje = "Vehículo desactivado.",
            onExito = onExito
        ) { repository.cambiarEstado(idVehiculo, "INACTIVO") }
    }

    private fun ejecutarAccion(
        exitoMensaje: String,
        onExito: () -> Unit,
        bloque: suspend () -> DetalleVehiculoDto
    ) {
        if (_estado.value.ocupado || _estado.value.fase != FaseDetalle.LISTO) return
        val actual = ++revision
        trabajo?.cancel()
        _estado.value = _estado.value.copy(ocupado = true, mensajeAccion = null, accionError = false)
        trabajo = viewModelScope.launch {
            try {
                val detalle = bloque()
                if (actual != revision) return@launch
                _estado.value = _estado.value.copy(
                    fase = FaseDetalle.LISTO,
                    detalle = detalle,
                    ocupado = false,
                    mensajeAccion = exitoMensaje,
                    accionError = false
                )
                onExito()
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (error: Exception) {
                if (actual != revision) return@launch
                val fallo = error as? FalloRegistroVehiculo
                if (fallo?.estadoHttp == 401) {
                    aplicarErrorCarga(error)
                    return@launch
                }
                _estado.value = _estado.value.copy(
                    ocupado = false,
                    mensajeAccion = mensajeAccion(fallo),
                    accionError = true
                )
            }
        }
    }

    private fun mensajeAccion(fallo: FalloRegistroVehiculo?): String = when (fallo?.codigo) {
        "PRINCIPAL_REQUIERE_REEMPLAZO" ->
            "Antes de desactivar este vehículo, establece otro vehículo activo como principal."
        "ULTIMO_ACTIVO" ->
            "Debes conservar al menos un vehículo activo."
        "LIMITE_ACTIVOS" ->
            "Alcanzaste el límite de vehículos activos de tu plan."
        "YA_ES_PRINCIPAL" -> "Este vehículo ya es el principal."
        "CANDIDATO_NO_ELEGIBLE" -> "Solo un vehículo activo puede ser principal."
        "ESTADO_SIN_CAMBIOS" -> "El vehículo ya está en ese estado."
        else -> fallo?.mensajeServidor ?: "No se pudo completar la acción. Inténtalo nuevamente."
    }

    private fun aplicarErrorCarga(error: Exception) {
        val fallo = error as? FalloRegistroVehiculo
        val sesion = fallo?.estadoHttp == 401
        val noDisponible = fallo?.codigo == "VEHICULO_NO_ENCONTRADO" || fallo?.estadoHttp == 404
        _estado.value = _estado.value.copy(
            fase = when {
                sesion -> FaseDetalle.SESION_REQUERIDA
                noDisponible -> FaseDetalle.NO_DISPONIBLE
                else -> FaseDetalle.ERROR
            },
            detalle = null,
            ocupado = false,
            mensaje = when {
                sesion -> "Inicia sesión para ver este vehículo."
                noDisponible -> "Este vehículo no está disponible en tu cuenta."
                fallo != null -> "No se pudo cargar el detalle (${fallo.codigo}). Inténtalo nuevamente."
                else -> "No se pudo cargar el detalle. Inténtalo nuevamente."
            }
        )
    }

    companion object {
        fun factory(idVehiculo: Long, repository: DetalleVehiculosRepository) = viewModelFactory {
            initializer { DetalleVehiculoViewModel(idVehiculo, repository) }
        }
    }
}
