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
    val mensaje: String? = null
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
        _estado.value = _estado.value.copy(fase = FaseDetalle.CARGANDO, detalle = null, mensaje = null)
        trabajo = viewModelScope.launch {
            try {
                val detalle = repository.consultar(idVehiculo)
                if (actual == revision) _estado.value = _estado.value.copy(fase = FaseDetalle.LISTO, detalle = detalle)
            } catch (cancelada: CancellationException) { throw cancelada }
            catch (error: Exception) {
                if (actual != revision) return@launch
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
                    mensaje = when {
                        sesion -> "Inicia sesión para ver este vehículo."
                        noDisponible -> "Este vehículo no está disponible en tu cuenta."
                        else -> "No se pudo cargar el detalle. Inténtalo nuevamente."
                    }
                )
            }
        }
    }

    companion object {
        fun factory(idVehiculo: Long, repository: DetalleVehiculosRepository) = viewModelFactory {
            initializer { DetalleVehiculoViewModel(idVehiculo, repository) }
        }
    }
}
