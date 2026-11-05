package com.mycar.app.features.historial.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.historial.domain.CodigoErrorHistorial
import com.mycar.app.features.historial.domain.DetalleMantenimientoHistorial
import com.mycar.app.features.historial.domain.ErrorHistorial
import com.mycar.app.features.historial.domain.HistorialRepository
import com.mycar.app.features.historial.domain.ModificarMantenimientoDatos
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FaseDetalle { CARGANDO, LISTO, EDITANDO, ERROR, ANULADO }

data class EstadoDetalleMantenimiento(
    val fase: FaseDetalle = FaseDetalle.CARGANDO,
    val detalle: DetalleMantenimientoHistorial? = null,
    val costoTexto: String = "",
    val observaciones: String = "",
    val taller: String = "",
    val mensaje: String? = null,
    val ocupado: Boolean = false
)

class DetalleMantenimientoViewModel(
    private val repository: HistorialRepository,
    private val idVehiculo: Long,
    private val idMantenimiento: Long
) : ViewModel() {
    private val mutable = MutableStateFlow(EstadoDetalleMantenimiento())
    val estado = mutable.asStateFlow()

    init { cargar() }

    fun cargar() {
        mutable.value = mutable.value.copy(fase = FaseDetalle.CARGANDO, mensaje = null, ocupado = false)
        viewModelScope.launch {
            try {
                val d = repository.detalle(idVehiculo, idMantenimiento)
                mutable.value = EstadoDetalleMantenimiento(
                    fase = FaseDetalle.LISTO,
                    detalle = d,
                    costoTexto = d.costo?.toPlainString().orEmpty(),
                    observaciones = d.observaciones.orEmpty(),
                    taller = d.taller.orEmpty()
                )
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    fase = FaseDetalle.ERROR,
                    mensaje = e.message ?: "No se pudo cargar el detalle"
                )
            }
        }
    }

    fun empezarEdicion() {
        val d = mutable.value.detalle ?: return
        if (!d.editable) return
        mutable.value = mutable.value.copy(fase = FaseDetalle.EDITANDO, mensaje = null)
    }

    fun cancelarEdicion() {
        val d = mutable.value.detalle ?: return
        mutable.value = mutable.value.copy(
            fase = FaseDetalle.LISTO,
            costoTexto = d.costo?.toPlainString().orEmpty(),
            observaciones = d.observaciones.orEmpty(),
            taller = d.taller.orEmpty(),
            mensaje = null
        )
    }

    fun actualizarCosto(v: String) { mutable.value = mutable.value.copy(costoTexto = v) }
    fun actualizarObservaciones(v: String) { mutable.value = mutable.value.copy(observaciones = v) }
    fun actualizarTaller(v: String) { mutable.value = mutable.value.copy(taller = v) }

    fun guardar() {
        val s = mutable.value
        if (s.ocupado || s.detalle?.editable != true) return
        val costo = s.costoTexto.trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { BigDecimal(it) }.getOrElse {
                mutable.value = s.copy(mensaje = "El costo no es válido")
                return
            }
        }
        if (costo != null && costo.signum() < 0) {
            mutable.value = s.copy(mensaje = "El costo no puede ser negativo")
            return
        }
        mutable.value = s.copy(ocupado = true, mensaje = null)
        viewModelScope.launch {
            try {
                val d = repository.modificar(
                    idVehiculo, idMantenimiento,
                    ModificarMantenimientoDatos(
                        costoTotal = costo,
                        observaciones = s.observaciones.trim().ifBlank { null },
                        taller = s.taller.trim().ifBlank { "Sin taller" }
                    )
                )
                mutable.value = EstadoDetalleMantenimiento(
                    fase = FaseDetalle.LISTO,
                    detalle = d,
                    costoTexto = d.costo?.toPlainString().orEmpty(),
                    observaciones = d.observaciones.orEmpty(),
                    taller = d.taller.orEmpty(),
                    mensaje = "Cambios guardados"
                )
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    ocupado = false,
                    mensaje = e.message ?: "No se pudieron guardar los cambios"
                )
            }
        }
    }

    fun anular() {
        val s = mutable.value
        if (s.ocupado || s.detalle?.anulable != true) return
        mutable.value = s.copy(ocupado = true, mensaje = null)
        viewModelScope.launch {
            try {
                repository.anular(idVehiculo, idMantenimiento)
                mutable.value = mutable.value.copy(fase = FaseDetalle.ANULADO, ocupado = false, mensaje = "Registro anulado")
            } catch (e: ErrorHistorial) {
                mutable.value = mutable.value.copy(
                    ocupado = false,
                    mensaje = when (e.codigo) {
                        CodigoErrorHistorial.ALERTA_ACTIVA ->
                            e.message + (e.idAlerta?.let { " (alerta #$it). Anúlala o atiéndela primero." } ?: "")
                        else -> e.message
                    }
                )
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(ocupado = false, mensaje = e.message ?: "No se pudo anular")
            }
        }
    }
}
