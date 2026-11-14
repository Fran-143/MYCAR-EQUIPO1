package com.mycar.app.features.vehiculos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.vehiculos.data.ContextoCorreccionKmDto
import com.mycar.app.features.vehiculos.data.SolicitudCorregirKmDto
import com.mycar.app.features.vehiculos.domain.ActualizarKilometrajeRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import com.mycar.app.features.vehiculos.domain.KilometrajeTexto
import java.util.UUID
import kotlin.math.floor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PasoCorreccionKm {
    CARGA, EDICION, VISTA_PREVIA, ENVIANDO, EXITO, ERROR, SESION, INACTIVO, NO_DISPONIBLE
}

enum class MotivoCorreccionKm(val etiqueta: String, val requiereDetalle: Boolean = false) {
    ERROR_ACCIDENTAL("Error accidental"),
    ERROR_DIGITACION("Error de digitación"),
    OTRO("Otro", requiereDetalle = true);

    companion object {
        fun porEtiqueta(valor: String?): MotivoCorreccionKm? =
            entries.find { it.etiqueta == valor }
    }
}

data class EstadoCorregirKm(
    val idVehiculo: Long,
    val idLectura: Long,
    val paso: PasoCorreccionKm = PasoCorreccionKm.CARGA,
    val contexto: ContextoCorreccionKmDto? = null,
    val kmTexto: String = "",
    val motivoOpcion: MotivoCorreccionKm? = null,
    val motivoDetalle: String = "",
    val errores: Map<String, String> = emptyMap(),
    val mensaje: String? = null,
    val idOperacion: UUID? = null,
    val kmConfirmado: Int? = null
) {
    fun motivoFinal(): String? {
        val opcion = motivoOpcion ?: return null
        return if (opcion.requiereDetalle) {
            val detalle = motivoDetalle.trim()
            if (detalle.isEmpty()) null else "${opcion.etiqueta}: $detalle"
        } else opcion.etiqueta
    }
}

class CorregirKilometrajeViewModel(
    private val idVehiculo: Long,
    private val idLectura: Long,
    private val repository: ActualizarKilometrajeRepository
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoCorregirKm(idVehiculo, idLectura))
    val estado = _estado.asStateFlow()

    init { cargar() }

    fun cargar() {
        _estado.value = _estado.value.copy(paso = PasoCorreccionKm.CARGA, mensaje = null, errores = emptyMap())
        viewModelScope.launch {
            try {
                val ctx = repository.contextoCorreccion(idVehiculo, idLectura)
                if (!ctx.elegible) {
                    _estado.value = _estado.value.copy(
                        paso = PasoCorreccionKm.NO_DISPONIBLE,
                        contexto = ctx,
                        mensaje = "No es posible corregir: se necesitan dos lecturas con al menos un kilometraje entero entre ellas."
                    )
                    return@launch
                }
                _estado.value = _estado.value.copy(paso = PasoCorreccionKm.EDICION, contexto = ctx)
            } catch (c: CancellationException) { throw c }
            catch (e: Exception) { manejarError(e) }
        }
    }

    fun cambiarKm(texto: String) {
        val soloEnteros = texto.filter { it.isDigit() }.take(7)
        _estado.value = _estado.value.copy(kmTexto = soloEnteros, errores = emptyMap())
    }

    fun cambiarMotivoOpcion(opcion: MotivoCorreccionKm) {
        _estado.value = _estado.value.copy(
            motivoOpcion = opcion,
            motivoDetalle = if (opcion.requiereDetalle) _estado.value.motivoDetalle else "",
            errores = emptyMap()
        )
    }

    fun cambiarMotivoDetalle(texto: String) {
        _estado.value = _estado.value.copy(motivoDetalle = texto.take(180), errores = emptyMap())
    }

    fun irVistaPrevia() {
        val ctx = _estado.value.contexto ?: return
        val pen = ctx.kmPenultimo ?: return
        val ult = ctx.kmUltimo ?: return
        val errores = mutableMapOf<String, String>()
        val km = _estado.value.kmTexto.toIntOrNull()
        if (km == null || km <= 0) {
            errores["kmCorregido"] = "Ingresa un kilometraje entero mayor que cero."
        } else if (km <= floor(pen).toInt() || km >= floor(ult).toInt()) {
            errores["kmCorregido"] =
                "El kilometraje corregido debe ser mayor que ${KilometrajeTexto.formatear(pen)} km y menor que ${KilometrajeTexto.formatear(ult)} km."
        }
        val opcion = _estado.value.motivoOpcion
        if (opcion == null) {
            errores["motivo"] = "Selecciona el motivo de la corrección."
        } else if (opcion.requiereDetalle) {
            val detalle = _estado.value.motivoDetalle.trim()
            val largo = detalle.codePointCount(0, detalle.length)
            if (largo < 5 || largo > 180) {
                errores["motivo"] = "Describe el motivo (entre 5 y 180 caracteres)."
            }
        }
        val motivo = _estado.value.motivoFinal()
        if (errores.isEmpty() && (motivo == null || motivo.codePointCount(0, motivo.length) > 200)) {
            errores["motivo"] = "Revisa el motivo de la corrección."
        }
        if (errores.isNotEmpty()) {
            _estado.value = _estado.value.copy(errores = errores)
            return
        }
        _estado.value = _estado.value.copy(paso = PasoCorreccionKm.VISTA_PREVIA, errores = emptyMap())
    }

    fun volverEdicion() { _estado.value = _estado.value.copy(paso = PasoCorreccionKm.EDICION) }

    fun confirmar() {
        val ctx = _estado.value.contexto ?: return
        val km = _estado.value.kmTexto.toIntOrNull() ?: return
        val motivo = _estado.value.motivoFinal() ?: return
        val op = _estado.value.idOperacion ?: UUID.randomUUID().also {
            _estado.value = _estado.value.copy(idOperacion = it)
        }
        _estado.value = _estado.value.copy(paso = PasoCorreccionKm.ENVIANDO, mensaje = null)
        viewModelScope.launch {
            try {
                val r = repository.corregir(
                    idVehiculo, idLectura,
                    SolicitudCorregirKmDto(op, km, motivo, ctx.idLectura, ctx.versionKm)
                )
                _estado.value = _estado.value.copy(
                    paso = PasoCorreccionKm.EXITO,
                    kmConfirmado = r.kmActual.toInt(),
                    idOperacion = null
                )
            } catch (c: CancellationException) { throw c }
            catch (e: Exception) { manejarError(e) }
        }
    }

    private fun manejarError(e: Exception) {
        val fallo = e as? FalloRegistroVehiculo
        when {
            fallo?.estadoHttp == 401 -> _estado.value = _estado.value.copy(
                paso = PasoCorreccionKm.SESION, mensaje = "Inicia sesión para continuar."
            )
            fallo?.codigo == "VEHICULO_INACTIVO" -> _estado.value = _estado.value.copy(
                paso = PasoCorreccionKm.INACTIVO, mensaje = "Solo vehículos activos."
            )
            fallo?.codigo == "CORRECCION_NO_DISPONIBLE" -> _estado.value = _estado.value.copy(
                paso = PasoCorreccionKm.NO_DISPONIBLE,
                mensaje = "No hay un valor entero válido entre la penúltima y la última lectura."
            )
            fallo?.codigo == "CORRECCION_YA_APLICADA" -> _estado.value = _estado.value.copy(
                paso = PasoCorreccionKm.NO_DISPONIBLE,
                mensaje = "Esta lectura ya fue corregida."
            )
            fallo?.codigo == "KM_OBSOLETO" || fallo?.codigo == "LECTURA_NO_ULTIMA" ->
                _estado.value = _estado.value.copy(
                    paso = PasoCorreccionKm.ERROR,
                    mensaje = "Los datos cambiaron. Vuelve al historial e inténtalo de nuevo."
                )
            fallo?.codigo == "KM_INTERVALO_INVALIDO" -> {
                val ctx = _estado.value.contexto
                _estado.value = _estado.value.copy(
                    paso = PasoCorreccionKm.EDICION,
                    errores = mapOf(
                        "kmCorregido" to
                            "El kilometraje corregido debe ser mayor que ${KilometrajeTexto.formatear(ctx?.kmPenultimo)} km y menor que ${KilometrajeTexto.formatear(ctx?.kmUltimo)} km."
                    )
                )
            }
            fallo?.codigo == "REFERENCIA_INCOMPATIBLE" -> _estado.value = _estado.value.copy(
                paso = PasoCorreccionKm.EDICION,
                errores = mapOf("kmCorregido" to "La corrección contradice mantenimientos históricos.")
            )
            else -> _estado.value = _estado.value.copy(
                paso = PasoCorreccionKm.ERROR, mensaje = "No se pudo completar la corrección."
            )
        }
    }

    companion object {
        fun factory(idVehiculo: Long, idLectura: Long, repository: ActualizarKilometrajeRepository) =
            viewModelFactory {
                initializer { CorregirKilometrajeViewModel(idVehiculo, idLectura, repository) }
            }
    }
}
