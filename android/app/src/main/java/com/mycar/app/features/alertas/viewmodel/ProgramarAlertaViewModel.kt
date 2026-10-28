package com.mycar.app.features.alertas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.alertas.data.ProgramarAlertaDto
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.ContextoProgramarAlerta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class BorradorAlerta(
    val codigoTipo: String = "",
    val criterio: String = "POR_KILOMETRAJE",
    val unidadTiempo: String = "MESES",
    val cantidadTiempo: String = "6",
    val intervaloKm: String = "5000",
    val servicioOtro: String = "",
    val idReferencia: Long? = null
)

data class EstadoProgramarAlerta(
    val idVehiculo: Long,
    val contexto: ContextoProgramarAlerta? = null,
    val borrador: BorradorAlerta = BorradorAlerta(),
    val cargando: Boolean = true,
    val enviando: Boolean = false,
    val exito: Long? = null,
    val errores: Map<String, String> = emptyMap(),
    val mensaje: String? = null
)

class ProgramarAlertaViewModel(
    private val repository: AlertasRepository,
    private val idVehiculo: Long,
    borradorInicial: BorradorAlerta? = null
) : ViewModel() {
    private val mutable = MutableStateFlow(EstadoProgramarAlerta(idVehiculo, borrador = borradorInicial ?: BorradorAlerta()))
    val estado = mutable.asStateFlow()

    init { cargar() }

    fun cargar() {
        viewModelScope.launch {
            mutable.value = mutable.value.copy(cargando = true, mensaje = null)
            try {
                val ctx = repository.contexto(idVehiculo)
                mutable.value = mutable.value.copy(contexto = ctx, cargando = false)
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(cargando = false, mensaje = e.message ?: "No se pudo cargar el contexto")
            }
        }
    }

    fun actualizar(cambio: (BorradorAlerta) -> BorradorAlerta) {
        mutable.value = mutable.value.copy(borrador = cambio(mutable.value.borrador), errores = emptyMap())
    }

    fun guardar() {
        val s = mutable.value
        if (s.enviando || s.contexto?.disponible != true) return
        val errores = validar(s.borrador)
        if (errores.isNotEmpty()) {
            mutable.value = s.copy(errores = errores); return
        }
        mutable.value = s.copy(enviando = true)
        viewModelScope.launch {
            try {
                val b = s.borrador
                val id = repository.programar(idVehiculo, ProgramarAlertaDto(
                    UUID.randomUUID().toString(), b.codigoTipo, b.criterio,
                    b.unidadTiempo.takeIf { b.criterio.contains("TIEMPO") },
                    b.cantidadTiempo.toIntOrNull()?.takeIf { b.criterio.contains("TIEMPO") },
                    b.intervaloKm.toIntOrNull()?.takeIf { b.criterio.contains("KILOMETRAJE") },
                    b.servicioOtro.takeIf { b.codigoTipo == "OTRO" },
                    b.idReferencia
                ))
                mutable.value = mutable.value.copy(enviando = false, exito = id)
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(enviando = false, mensaje = e.message ?: "No se pudo programar la alerta")
            }
        }
    }

    private fun validar(b: BorradorAlerta): Map<String, String> = buildMap {
        if (b.codigoTipo.isBlank()) put("codigoTipo", "Selecciona un tipo")
        if (b.criterio.contains("TIEMPO") && b.cantidadTiempo.toIntOrNull()?.let { it > 0 } != true)
            put("cantidadTiempo", "Cantidad inválida")
        if (b.criterio.contains("KILOMETRAJE") && b.intervaloKm.toIntOrNull()?.let { it > 0 } != true)
            put("intervaloKm", "Intervalo inválido")
        if (b.codigoTipo == "OTRO" && b.servicioOtro.isBlank()) put("servicioOtro", "Describe el servicio")
    }
}
