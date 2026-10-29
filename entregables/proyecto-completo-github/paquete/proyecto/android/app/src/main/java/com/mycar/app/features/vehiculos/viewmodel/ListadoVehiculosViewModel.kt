package com.mycar.app.features.vehiculos.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.vehiculos.data.ListadoVehiculosDto
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import com.mycar.app.features.vehiculos.domain.ListadoVehiculosRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FaseListado { CARGANDO, LISTO, ERROR, SESION_REQUERIDA }
data class EstadoListadoVehiculos(
    val filtro: String = "TODOS", val busqueda: String = "",
    val fase: FaseListado = FaseListado.CARGANDO,
    val resultado: ListadoVehiculosDto? = null, val mensaje: String? = null
)

/** Una instancia por cuenta/ciclo de sesión, igual que RegistroVehiculoRoute. */
class ListadoVehiculosViewModel(private val saved: SavedStateHandle,
                               private val repository: ListadoVehiculosRepository) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoListadoVehiculos(
        filtro = saved["rf09.filtro"] ?: "TODOS", busqueda = saved["rf09.busqueda"] ?: ""))
    val estado = _estado.asStateFlow()
    private var trabajo: Job? = null
    private var revision = 0L

    fun cambiarBusqueda(texto: String) { _estado.value = _estado.value.copy(busqueda = texto); saved["rf09.busqueda"] = texto }
    fun filtrar(filtro: String) {
        if (filtro !in setOf("TODOS", "ACTIVO", "INACTIVO")) return
        _estado.value = _estado.value.copy(filtro = filtro); saved["rf09.filtro"] = filtro; cargar()
    }
    fun cargar() {
        val actual = ++revision
        trabajo?.cancel()
        val filtro = _estado.value.filtro
        val busqueda = _estado.value.busqueda.trim().replace(Regex("\\s+"), " ")
        _estado.value = _estado.value.copy(fase = FaseListado.CARGANDO, resultado = null, mensaje = null)
        trabajo = viewModelScope.launch {
            try {
                val lista = repository.listar(filtro, busqueda)
                if (actual == revision) _estado.value = _estado.value.copy(fase = FaseListado.LISTO, resultado = lista)
            } catch (cancelada: CancellationException) { throw cancelada }
            catch (error: Exception) {
                if (actual != revision) return@launch
                val sesion = error is FalloRegistroVehiculo && error.estadoHttp == 401
                val cuenta = error is FalloRegistroVehiculo && error.codigo == "CUENTA_NO_AUTORIZADA"
                _estado.value = _estado.value.copy(fase = if (sesion) FaseListado.SESION_REQUERIDA else FaseListado.ERROR,
                    resultado = null, mensaje = when {
                        sesion -> "Inicia sesión para ver tus vehículos."
                        cuenta -> "Tu cuenta no tiene acceso a esta consulta."
                        else -> "No se pudieron consultar tus vehículos. Inténtalo nuevamente."
                    })
            }
        }
    }
    companion object {
        fun factory(repository: ListadoVehiculosRepository, estadoEnSistema: Boolean = true) = viewModelFactory {
            initializer { ListadoVehiculosViewModel(if (estadoEnSistema) createSavedStateHandle() else SavedStateHandle(), repository) }
        }
    }
}
