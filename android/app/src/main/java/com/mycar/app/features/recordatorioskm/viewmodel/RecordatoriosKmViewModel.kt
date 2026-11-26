package com.mycar.app.features.recordatorioskm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.recordatorioskm.domain.ErrorRecordatorioKm
import com.mycar.app.features.recordatorioskm.domain.FrecuenciaKm
import com.mycar.app.features.recordatorioskm.domain.RecordatoriosKmRepository
import com.mycar.app.features.recordatorioskm.domain.VehiculoRecordatorioKm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EstadoRecordatoriosKm(
    val cargando: Boolean = true,
    val guardandoGlobal: Boolean = false,
    val activo: Boolean = false,
    val vehiculos: List<VehiculoRecordatorioKm> = emptyList(),
    val frecuenciasLocales: Map<Long, FrecuenciaKm> = emptyMap(),
    val guardandoId: Long? = null,
    val mensajeError: String? = null,
    val modalExitoNombre: String? = null,
    val modalSalirPendiente: Boolean = false,
    val sesionInvalida: Boolean = false
) {
    val hayCambiosPendientes: Boolean
        get() = vehiculos.any { v ->
            (frecuenciasLocales[v.idVehiculo] ?: v.frecuencia) != v.frecuencia
        }
}

class RecordatoriosKmViewModel(
    private val repository: RecordatoriosKmRepository
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoRecordatoriosKm())
    val estado: StateFlow<EstadoRecordatoriosKm> = _estado.asStateFlow()

    private var salidaPendiente: (() -> Unit)? = null
    private var cargaEnCurso = false

    /**
     * Recarga configuración + vehículos activos.
     * Se invoca al entrar/regresar a la pantalla (no depende del interruptor).
     * Conserva frecuencias locales pendientes de guardar.
     */
    fun cargar() {
        if (cargaEnCurso) return
        viewModelScope.launch {
            cargaEnCurso = true
            val primerCarga = _estado.value.vehiculos.isEmpty() && !_estado.value.hayCambiosPendientes
            _estado.value = _estado.value.copy(
                cargando = primerCarga || _estado.value.cargando,
                mensajeError = null
            )
            try {
                val cfg = repository.consultar()
                _estado.value = _estado.value.copy(
                    cargando = false,
                    activo = cfg.activo,
                    vehiculos = cfg.vehiculos,
                    frecuenciasLocales = mergeLocalesTrasRecarga(cfg.vehiculos),
                    mensajeError = null
                )
            } catch (e: ErrorRecordatorioKm.Sesion) {
                _estado.value = _estado.value.copy(cargando = false, sesionInvalida = true, mensajeError = e.message)
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargando = false,
                    mensajeError = e.message ?: "No se pudieron cargar los recordatorios."
                )
            } finally {
                cargaEnCurso = false
            }
        }
    }

    fun setActivo(activo: Boolean) {
        if (_estado.value.guardandoGlobal || _estado.value.cargando) return
        val anterior = _estado.value.activo
        if (anterior == activo) return
        viewModelScope.launch {
            _estado.value = _estado.value.copy(
                activo = activo,
                guardandoGlobal = true,
                mensajeError = null
            )
            try {
                val cfg = repository.actualizarGlobal(activo)
                _estado.value = _estado.value.copy(
                    guardandoGlobal = false,
                    activo = cfg.activo,
                    vehiculos = cfg.vehiculos,
                    frecuenciasLocales = mergeLocalesTrasRecarga(cfg.vehiculos)
                )
            } catch (e: ErrorRecordatorioKm.Sesion) {
                _estado.value = _estado.value.copy(
                    guardandoGlobal = false,
                    activo = anterior,
                    sesionInvalida = true,
                    mensajeError = e.message
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    guardandoGlobal = false,
                    activo = anterior,
                    mensajeError = e.message ?: "No se pudo guardar el interruptor."
                )
            }
        }
    }

    fun setFrecuenciaLocal(idVehiculo: Long, frecuencia: FrecuenciaKm) {
        _estado.value = _estado.value.copy(
            frecuenciasLocales = _estado.value.frecuenciasLocales + (idVehiculo to frecuencia),
            mensajeError = null
        )
    }

    fun guardarPreferencia(idVehiculo: Long) {
        if (_estado.value.guardandoId != null || _estado.value.cargando) return
        val local = _estado.value.frecuenciasLocales[idVehiculo]
            ?: _estado.value.vehiculos.firstOrNull { it.idVehiculo == idVehiculo }?.frecuencia
            ?: return
        val vehiculo = _estado.value.vehiculos.firstOrNull { it.idVehiculo == idVehiculo } ?: return
        viewModelScope.launch {
            _estado.value = _estado.value.copy(guardandoId = idVehiculo, mensajeError = null)
            try {
                val actualizado = repository.guardarFrecuencia(idVehiculo, local)
                val nombre = listOf(vehiculo.marca, vehiculo.modelo).filter { it.isNotBlank() }.joinToString(" ")
                    .ifBlank { "tu vehículo" }
                val vehiculos = _estado.value.vehiculos.map { v ->
                    if (v.idVehiculo == idVehiculo) v.copy(
                        frecuencia = actualizado.frecuencia,
                        proximoRecordatorio = actualizado.proximoRecordatorio,
                        sinLecturaValida = actualizado.sinLecturaValida,
                        pendiente = actualizado.pendiente
                    ) else v
                }
                _estado.value = _estado.value.copy(
                    guardandoId = null,
                    vehiculos = vehiculos,
                    frecuenciasLocales = _estado.value.frecuenciasLocales + (idVehiculo to actualizado.frecuencia),
                    modalExitoNombre = nombre
                )
            } catch (e: ErrorRecordatorioKm.Sesion) {
                _estado.value = _estado.value.copy(
                    guardandoId = null,
                    sesionInvalida = true,
                    mensajeError = e.message
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    guardandoId = null,
                    mensajeError = e.message ?: "No se pudo guardar la preferencia."
                )
            }
        }
    }

    fun cerrarModalExito() {
        _estado.value = _estado.value.copy(modalExitoNombre = null)
    }

    fun intentarSalir(continuar: () -> Unit) {
        if (_estado.value.hayCambiosPendientes) {
            salidaPendiente = continuar
            _estado.value = _estado.value.copy(modalSalirPendiente = true)
        } else {
            continuar()
        }
    }

    fun seguirEditando() {
        salidaPendiente = null
        _estado.value = _estado.value.copy(modalSalirPendiente = false)
    }

    fun salirSinGuardar() {
        val cont = salidaPendiente
        salidaPendiente = null
        val restaurado = _estado.value.vehiculos.associate { it.idVehiculo to it.frecuencia }
        _estado.value = _estado.value.copy(
            modalSalirPendiente = false,
            frecuenciasLocales = restaurado
        )
        cont?.invoke()
    }

    private fun mergeLocalesTrasRecarga(vehiculos: List<VehiculoRecordatorioKm>): Map<Long, FrecuenciaKm> {
        val prev = _estado.value.frecuenciasLocales
        return vehiculos.associate { v ->
            val local = prev[v.idVehiculo]
            v.idVehiculo to if (local != null && local != v.frecuencia) local else v.frecuencia
        }
    }

    companion object {
        fun factory(repository: RecordatoriosKmRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(RecordatoriosKmViewModel::class.java))
                    return RecordatoriosKmViewModel(repository) as T
                }
            }
    }
}
