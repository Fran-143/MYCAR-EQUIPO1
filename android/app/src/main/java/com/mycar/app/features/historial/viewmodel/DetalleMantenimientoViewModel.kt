package com.mycar.app.features.historial.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.historial.domain.CodigoErrorHistorial
import com.mycar.app.features.historial.domain.DetalleMantenimientoHistorial
import com.mycar.app.features.historial.domain.ErrorHistorial
import com.mycar.app.features.historial.domain.HistorialRepository
import com.mycar.app.features.historial.domain.ModificarMantenimientoDatos
import com.mycar.app.features.talleres.domain.Taller
import com.mycar.app.features.talleres.domain.TalleresRepository
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FaseDetalle { CARGANDO, LISTO, EDITANDO, ERROR, ANULADO }

/** RF20: Mantener / Sin taller / Seleccionar del catálogo ACTIVO. */
enum class ModoTaller { SIN_TALLER, MANTENER, SELECCIONAR }

data class EstadoDetalleMantenimiento(
    val fase: FaseDetalle = FaseDetalle.CARGANDO,
    val detalle: DetalleMantenimientoHistorial? = null,
    val costoTexto: String = "",
    val observaciones: String = "",
    val modoTaller: ModoTaller = ModoTaller.MANTENER,
    val idTallerSeleccionado: Long? = null,
    val talleresActivos: List<Taller> = emptyList(),
    val errorTaller: String? = null,
    val mensaje: String? = null,
    val ocupado: Boolean = false
)

class DetalleMantenimientoViewModel(
    private val repository: HistorialRepository,
    private val idVehiculo: Long,
    private val idMantenimiento: Long,
    private val talleresRepository: TalleresRepository? = null
) : ViewModel() {
    private val mutable = MutableStateFlow(EstadoDetalleMantenimiento())
    val estado = mutable.asStateFlow()

    init { cargar() }

    fun cargar() {
        mutable.value = mutable.value.copy(fase = FaseDetalle.CARGANDO, mensaje = null, ocupado = false)
        viewModelScope.launch {
            try {
                val d = repository.detalle(idVehiculo, idMantenimiento)
                val talleres = runCatching { talleresRepository?.listarActivos().orEmpty() }.getOrDefault(emptyList())
                mutable.value = EstadoDetalleMantenimiento(
                    fase = FaseDetalle.LISTO,
                    detalle = d,
                    costoTexto = d.costo?.toPlainString().orEmpty(),
                    observaciones = d.observaciones.orEmpty(),
                    modoTaller = if (d.taller.isNullOrBlank() && d.idTaller == null) ModoTaller.SIN_TALLER else ModoTaller.MANTENER,
                    idTallerSeleccionado = null,
                    talleresActivos = talleres
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
        if (!d.editable) {
            mutable.value = mutable.value.copy(mensaje = "Este mantenimiento no se puede editar")
            return
        }
        viewModelScope.launch {
            val talleres = runCatching { talleresRepository?.listarActivos().orEmpty() }
                .getOrDefault(mutable.value.talleresActivos)
            mutable.value = mutable.value.copy(
                fase = FaseDetalle.EDITANDO,
                mensaje = null,
                costoTexto = d.costo?.toPlainString().orEmpty(),
                observaciones = d.observaciones.orEmpty(),
                modoTaller = if (d.taller.isNullOrBlank() && d.idTaller == null) ModoTaller.SIN_TALLER else ModoTaller.MANTENER,
                idTallerSeleccionado = null,
                talleresActivos = talleres
            )
        }
    }

    /** EA06: cancelar recarga detalle desde API. */
    fun cancelarEdicion() {
        cargar()
    }

    fun actualizarCosto(v: String) {
        mutable.value = mutable.value.copy(costoTexto = v, mensaje = null)
    }
    fun actualizarObservaciones(v: String) {
        mutable.value = mutable.value.copy(observaciones = v, mensaje = null)
    }
    fun actualizarIdTallerSeleccionado(id: Long?) {
        mutable.value = mutable.value.copy(
            idTallerSeleccionado = id,
            modoTaller = ModoTaller.SELECCIONAR,
            errorTaller = null,
            mensaje = null
        )
    }
    fun actualizarModoTaller(modo: ModoTaller) {
        val actual = mutable.value
        mutable.value = when (modo) {
            ModoTaller.SIN_TALLER -> actual.copy(
                modoTaller = modo, idTallerSeleccionado = null, errorTaller = null, mensaje = null
            )
            ModoTaller.MANTENER -> actual.copy(
                modoTaller = modo, idTallerSeleccionado = null, errorTaller = null, mensaje = null
            )
            ModoTaller.SELECCIONAR -> actual.copy(modoTaller = modo, errorTaller = null, mensaje = null)
        }
    }

    fun guardar() {
        val s = mutable.value
        if (s.ocupado || s.detalle?.editable != true) return
        val costo = s.costoTexto.trim().takeIf { it.isNotEmpty() }?.let {
            runCatching { BigDecimal(it) }.getOrElse {
                mutable.value = s.copy(mensaje = "El costo no es válido", errorTaller = null)
                return
            }
        }
        if (costo != null && costo.signum() < 0) {
            mutable.value = s.copy(mensaje = "El costo no puede ser negativo", errorTaller = null)
            return
        }
        val datos = when (s.modoTaller) {
            ModoTaller.SIN_TALLER -> ModificarMantenimientoDatos(
                costoTotal = costo,
                observaciones = s.observaciones.trim().ifBlank { null },
                sinTaller = true
            )
            ModoTaller.MANTENER -> ModificarMantenimientoDatos(
                costoTotal = costo,
                observaciones = s.observaciones.trim().ifBlank { null }
            )
            ModoTaller.SELECCIONAR -> {
                val id = s.idTallerSeleccionado
                if (id == null || id <= 0) {
                    mutable.value = s.copy(
                        errorTaller = "Selecciona un taller del catálogo o elige Sin taller",
                        mensaje = "Selecciona un taller del catálogo o elige Sin taller"
                    )
                    return
                }
                ModificarMantenimientoDatos(
                    costoTotal = costo,
                    observaciones = s.observaciones.trim().ifBlank { null },
                    idTaller = id
                )
            }
        }
        mutable.value = s.copy(ocupado = true, mensaje = null, errorTaller = null)
        viewModelScope.launch {
            try {
                val d = repository.modificar(idVehiculo, idMantenimiento, datos)
                val talleres = runCatching { talleresRepository?.listarActivos().orEmpty() }
                    .getOrDefault(s.talleresActivos)
                mutable.value = EstadoDetalleMantenimiento(
                    fase = FaseDetalle.LISTO,
                    detalle = d,
                    costoTexto = d.costo?.toPlainString().orEmpty(),
                    observaciones = d.observaciones.orEmpty(),
                    modoTaller = if (d.taller.isNullOrBlank() && d.idTaller == null) ModoTaller.SIN_TALLER else ModoTaller.MANTENER,
                    idTallerSeleccionado = null,
                    talleresActivos = talleres,
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
                // RF21 B6: éxito solo tras COMMIT; la UI navega al historial vía fase ANULADO.
                mutable.value = mutable.value.copy(fase = FaseDetalle.ANULADO, ocupado = false, mensaje = "Registro anulado")
            } catch (e: ErrorHistorial) {
                // EA03/EA04: refrescar flags (anulable / bloqueadoPorAlertaActiva).
                if (e.codigo == CodigoErrorHistorial.ALERTA_ACTIVA) {
                    try {
                        val d = repository.detalle(idVehiculo, idMantenimiento)
                        mutable.value = mutable.value.copy(
                            fase = FaseDetalle.LISTO,
                            detalle = d,
                            ocupado = false,
                            mensaje = e.message + (e.idAlerta?.let { " (alerta #$it). Atiéndela o anúlala primero." } ?: "")
                        )
                    } catch (_: Exception) {
                        mutable.value = mutable.value.copy(
                            ocupado = false,
                            mensaje = e.message + (e.idAlerta?.let { " (alerta #$it). Atiéndela o anúlala primero." } ?: "")
                        )
                    }
                } else {
                    mutable.value = mutable.value.copy(ocupado = false, mensaje = e.message)
                }
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(ocupado = false, mensaje = e.message ?: "No se pudo anular")
            }
        }
    }
}
