package com.mycar.app.features.vehiculos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.vehiculos.data.ContextoKilometrajeDto
import com.mycar.app.features.vehiculos.data.SolicitudActualizarKmDto
import com.mycar.app.features.vehiculos.domain.ActualizarKilometrajeRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PasoKm { CARGA, EDICION, VISTA_PREVIA, ENVIANDO, EXITO, ERROR, SESION, INACTIVO, SIN_LECTURA }
data class EstadoActualizarKm(
    val idVehiculo: Long,
    val paso: PasoKm = PasoKm.CARGA,
    val contexto: ContextoKilometrajeDto? = null,
    val kmNuevoTexto: String = "",
    val errores: Map<String, String> = emptyMap(),
    val mensaje: String? = null,
    val idOperacion: UUID? = null,
    val kmConfirmado: Int? = null
)

/** Sin SavedStateHandle: el host de cuenta no es SavedStateRegistryOwner y createSavedStateHandle cerraba la app. */
class ActualizarKilometrajeViewModel(
    private val idVehiculo: Long,
    private val repository: ActualizarKilometrajeRepository
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoActualizarKm(idVehiculo))
    val estado = _estado.asStateFlow()

    init { cargarContexto() }

    fun cargarContexto() {
        _estado.value = _estado.value.copy(paso = PasoKm.CARGA, mensaje = null, errores = emptyMap())
        viewModelScope.launch {
            try {
                val ctx = repository.contexto(idVehiculo)
                if (ctx.estadoVehiculo != "ACTIVO") {
                    _estado.value = _estado.value.copy(paso = PasoKm.INACTIVO, contexto = ctx,
                        mensaje = "Solo puedes actualizar vehículos activos.")
                    return@launch
                }
                _estado.value = _estado.value.copy(paso = PasoKm.EDICION, contexto = ctx)
            } catch (cancelada: CancellationException) { throw cancelada }
            catch (e: Exception) { manejarError(e, reintentar = ::cargarContexto) }
        }
    }

    fun cambiarKm(texto: String) {
        val actual = _estado.value
        if (texto == actual.kmNuevoTexto) return
        val pendiente = actual.idOperacion
        if (pendiente == null) {
            _estado.value = actual.copy(kmNuevoTexto = texto, errores = emptyMap())
            return
        }
        // Resultado incierto: no descartar idOperacion en silencio; reconciliar primero.
        viewModelScope.launch {
            reconciliarAntesDeCambiarKm(pendiente, texto)
        }
    }

    private suspend fun reconciliarAntesDeCambiarKm(op: UUID, textoNuevo: String) {
        try {
            val estadoOp = repository.operacion(op)
            when (estadoOp.estado) {
                "CONFIRMADA" -> {
                    val km = estadoOp.resultado?.kmActual
                    _estado.value = _estado.value.copy(
                        paso = PasoKm.EXITO,
                        kmConfirmado = km,
                        idOperacion = null,
                        mensaje = null,
                        errores = emptyMap()
                    )
                }
                "EN_PROCESO" -> {
                    _estado.value = _estado.value.copy(
                        paso = PasoKm.ERROR,
                        mensaje = "La operación sigue en proceso. Inténtalo de nuevo en unos segundos.",
                        errores = emptyMap()
                    )
                }
                else -> {
                    // Operación no reutilizable: permitir nuevo texto; nueva identidad solo al confirmar.
                    _estado.value = _estado.value.copy(
                        kmNuevoTexto = textoNuevo,
                        idOperacion = null,
                        errores = emptyMap(),
                        mensaje = null,
                        paso = PasoKm.EDICION
                    )
                }
            }
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (_: Exception) {
            // Sin certeza del servidor: conservar identidad y no aplicar el cambio de km.
            _estado.value = _estado.value.copy(
                paso = PasoKm.ERROR,
                mensaje = "No se pudo confirmar el resultado. Conserva la misma operación antes de reintentar."
            )
        }
    }

    fun irVistaPrevia() {
        val ctx = _estado.value.contexto ?: return
        val km = _estado.value.kmNuevoTexto.trim().toIntOrNull()
        val errores = mutableMapOf<String, String>()
        if (km == null || km <= 0) errores["kmNuevo"] = "Ingresa un kilometraje entero mayor que cero."
        else if (km <= ctx.kmActual) errores["kmNuevo"] = "Debe ser mayor que ${ctx.kmActual} km."
        if (errores.isNotEmpty()) {
            _estado.value = _estado.value.copy(errores = errores)
            return
        }
        _estado.value = _estado.value.copy(paso = PasoKm.VISTA_PREVIA, errores = emptyMap())
    }

    fun volverEdicion() { _estado.value = _estado.value.copy(paso = PasoKm.EDICION) }

    fun confirmar() {
        val ctx = _estado.value.contexto ?: return
        val km = _estado.value.kmNuevoTexto.trim().toIntOrNull() ?: return
        // Conserva idOperacion en reintentos de la misma confirmación (sin duplicar identidad).
        val op = _estado.value.idOperacion ?: UUID.randomUUID().also {
            _estado.value = _estado.value.copy(idOperacion = it)
        }
        _estado.value = _estado.value.copy(paso = PasoKm.ENVIANDO, mensaje = null)
        viewModelScope.launch {
            try {
                val resultado = repository.registrar(
                    idVehiculo,
                    SolicitudActualizarKmDto(op, km, ctx.idLectura, ctx.versionKm)
                )
                _estado.value = _estado.value.copy(
                    paso = PasoKm.EXITO, kmConfirmado = resultado.kmActual, idOperacion = null, kmNuevoTexto = ""
                )
            } catch (cancelada: CancellationException) { throw cancelada }
            catch (e: Exception) {
                if (e is FalloRegistroVehiculo && e.codigo == "OPERACION_EN_PROCESO") {
                    recuperarOperacion(op)
                    return@launch
                }
                manejarError(e, reintentar = ::confirmar)
            }
        }
    }

    private suspend fun recuperarOperacion(op: UUID) {
        try {
            when (repository.operacion(op).estado) {
                "CONFIRMADA" -> {
                    val km = repository.operacion(op).resultado?.kmActual
                    _estado.value = _estado.value.copy(paso = PasoKm.EXITO, kmConfirmado = km, idOperacion = null)
                }
                "EN_PROCESO" -> _estado.value = _estado.value.copy(
                    paso = PasoKm.ERROR,
                    mensaje = "La operación sigue en proceso. Inténtalo de nuevo en unos segundos."
                )
                else -> _estado.value = _estado.value.copy(
                    paso = PasoKm.ERROR,
                    mensaje = "No se pudo confirmar el resultado. Conserva la misma operación antes de reintentar."
                )
            }
        } catch (e: Exception) {
            manejarError(e, reintentar = { viewModelScope.launch { recuperarOperacion(op) } })
        }
    }

    private fun manejarError(e: Exception, reintentar: () -> Unit) {
        val fallo = e as? FalloRegistroVehiculo
        when {
            fallo?.estadoHttp == 401 -> _estado.value = _estado.value.copy(paso = PasoKm.SESION, mensaje = "Inicia sesión para continuar.")
            fallo?.codigo == "KILOMETRAJE_NO_DISPONIBLE" -> _estado.value = _estado.value.copy(paso = PasoKm.SIN_LECTURA, mensaje = "No hay lectura disponible.")
            fallo?.codigo == "KM_OBSOLETO" -> {
                _estado.value = _estado.value.copy(paso = PasoKm.ERROR, mensaje = "El kilometraje cambió. Recarga e inténtalo otra vez.")
            }
            fallo?.codigo == "KM_NO_SUPERIOR" -> _estado.value = _estado.value.copy(
                paso = PasoKm.EDICION, errores = mapOf("kmNuevo" to "Debe ser mayor al kilometraje vigente.")
            )
            fallo?.codigo == "VEHICULO_INACTIVO" -> _estado.value = _estado.value.copy(paso = PasoKm.INACTIVO, mensaje = "Vehículo inactivo.")
            else -> _estado.value = _estado.value.copy(paso = PasoKm.ERROR, mensaje = "No se pudo completar la actualización.")
        }
    }

    companion object {
        fun factory(idVehiculo: Long, repository: ActualizarKilometrajeRepository) = viewModelFactory {
            initializer { ActualizarKilometrajeViewModel(idVehiculo, repository) }
        }
    }
}
