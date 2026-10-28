package com.mycar.app.features.alertas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.DatosAlerta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FaseAlertas { CARGANDO, LISTO, ERROR, VACIO }

data class EstadoAlertas(
    val fase: FaseAlertas = FaseAlertas.CARGANDO,
    val vehiculoId: Long? = null,
    val sincronizadas: Int = 0,
    val alertas: List<DatosAlerta> = emptyList(),
    val mensaje: String? = null
)

class AlertasViewModel(
    private val repository: AlertasRepository,
    private val vehiculos: List<Pair<Long, String>>,
    idInicial: Long?
) : ViewModel() {
    private val mutable = MutableStateFlow(EstadoAlertas(vehiculoId = idInicial ?: vehiculos.firstOrNull()?.first))
    val estado = mutable.asStateFlow()

    init { mutable.value.vehiculoId?.let { cargar(it) } }

    fun seleccionar(id: Long) {
        if (mutable.value.vehiculoId == id) return
        mutable.value = mutable.value.copy(vehiculoId = id, fase = FaseAlertas.CARGANDO)
        cargar(id)
    }

    fun recargar() { mutable.value.vehiculoId?.let { cargar(it) } }

    private fun cargar(id: Long) {
        viewModelScope.launch {
            try {
                val (sync, lista) = repository.listar(id)
                mutable.value = mutable.value.copy(
                    sincronizadas = sync,
                    alertas = lista,
                    fase = if (lista.isEmpty()) FaseAlertas.VACIO else FaseAlertas.LISTO,
                    mensaje = null
                )
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(fase = FaseAlertas.ERROR, mensaje = e.message ?: "No se pudieron cargar las alertas")
            }
        }
    }
}
