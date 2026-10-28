package com.mycar.app.features.vehiculos.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.Gson
import com.mycar.app.features.vehiculos.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegistroVehiculoViewModel(
    private val savedState: SavedStateHandle,
    repository: RegistroVehiculoRepository,
    pendientes: SolicitudRegistroPendienteStore
) : ViewModel() {
    private val gson = Gson()
    private val coordinador = CoordinadorRegistroVehiculo(repository, pendientes,
        runCatching { savedState.get<String>(BORRADOR)?.let { gson.fromJson(it, FormularioRegistroVehiculo::class.java) } }
            .getOrNull() ?: FormularioRegistroVehiculo())
    private val _estado = MutableStateFlow(coordinador.estado)
    val estado = _estado.asStateFlow()

    fun cambiar(campo: String, valor: String) {
        if (_estado.value.ocupado) return
        coordinador.cambiar(campo, valor)
        publicar()
    }
    fun cargar() = ejecutar({ coordinador.cargar() })
    fun guardar() = ejecutar({ coordinador.guardar() })
    fun comprobar() = ejecutar({ coordinador.comprobar() })
    fun reenviar() = ejecutar({ coordinador.reenviar() })
    fun finalizar(onNavegar: () -> Unit) {
        var terminado = false
        ejecutar({ terminado = coordinador.finalizarConfirmacion() }, { if (terminado) onNavegar() })
    }

    private fun ejecutar(accion: suspend () -> Unit, despues: () -> Unit = {}) {
        if (_estado.value.ocupado) return
        _estado.value = _estado.value.copy(ocupado = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { accion() }
                publicar()
                despues()
            } catch (cancelada: CancellationException) { throw cancelada }
            catch (_: Exception) {
                _estado.value = coordinador.estado.copy(ocupado = false,
                    mensaje = "No se pudo completar la acción. Inténtalo nuevamente.")
            }
        }
    }
    private fun publicar() {
        _estado.value = coordinador.estado.copy(ocupado = false)
        savedState[BORRADOR] = gson.toJson(coordinador.estado.formulario)
    }
    companion object {
        private const val BORRADOR = "rf08.formulario.v1"
        fun factory(repository: RegistroVehiculoRepository, pendientes: SolicitudRegistroPendienteStore, estadoEnSistema: Boolean = true) = viewModelFactory {
            initializer { RegistroVehiculoViewModel(if (estadoEnSistema) createSavedStateHandle() else SavedStateHandle(), repository, pendientes) }
        }
    }
}
