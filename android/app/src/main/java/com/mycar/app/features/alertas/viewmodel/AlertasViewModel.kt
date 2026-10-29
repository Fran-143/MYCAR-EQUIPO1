package com.mycar.app.features.alertas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.DatosAlerta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FaseAlertas { CARGANDO, LISTO, ERROR, VACIO }

/** Filtros RF23!G45. ANULADA no es chip (G43). */
enum class FiltroAlertas(val etiqueta: String, val estadoApi: String?) {
    TODAS("Todas", null),
    PROGRAMADAS("Programadas", "PROGRAMADA"),
    PROXIMAS("Próximas", "PROXIMA"),
    PENDIENTES("Pendientes", "PENDIENTE"),
    VENCIDAS("Vencidas", "VENCIDA"),
    REALIZADAS("Realizadas", "REALIZADA");
}

data class EstadoAlertas(
    val fase: FaseAlertas = FaseAlertas.CARGANDO,
    val vehiculoId: Long? = null,
    val sincronizadas: Int = 0,
    val alertas: List<DatosAlerta> = emptyList(),
    val visibles: List<DatosAlerta> = emptyList(),
    val filtro: FiltroAlertas = FiltroAlertas.TODAS,
    val mensaje: String? = null,
    val generacion: Int = 0
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
        if (mutable.value.vehiculoId == id &&
            (mutable.value.fase == FaseAlertas.LISTO || mutable.value.fase == FaseAlertas.VACIO)) return
        mutable.value = mutable.value.copy(
            vehiculoId = id, fase = FaseAlertas.CARGANDO, filtro = FiltroAlertas.TODAS
        )
        cargar(id)
    }

    fun cambiarFiltro(filtro: FiltroAlertas) {
        val s = mutable.value
        val visibles = filtrar(s.alertas, filtro)
        mutable.value = s.copy(
            filtro = filtro,
            visibles = visibles,
            fase = when {
                s.fase == FaseAlertas.ERROR || s.fase == FaseAlertas.CARGANDO -> s.fase
                visibles.isEmpty() -> FaseAlertas.VACIO
                else -> FaseAlertas.LISTO
            }
        )
    }

    /** G108: al volver de programar, filtro Todas. */
    fun reiniciarFiltroTodasYRecargar() {
        mutable.value = mutable.value.copy(filtro = FiltroAlertas.TODAS)
        recargar()
    }

    fun recargar() {
        val id = mutable.value.vehiculoId ?: vehiculos.firstOrNull()?.first ?: return
        mutable.value = mutable.value.copy(vehiculoId = id, fase = FaseAlertas.CARGANDO)
        cargar(id)
    }

    private fun cargar(id: Long) {
        val gen = mutable.value.generacion + 1
        mutable.value = mutable.value.copy(generacion = gen)
        viewModelScope.launch {
            try {
                val (sync, lista) = repository.listar(id)
                val s = mutable.value
                if (s.vehiculoId != id || s.generacion != gen) return@launch
                val sinAnuladas = lista.filter { it.estado != "ANULADA" }
                val visibles = filtrar(sinAnuladas, s.filtro)
                mutable.value = s.copy(
                    sincronizadas = sync,
                    alertas = sinAnuladas,
                    visibles = visibles,
                    fase = if (visibles.isEmpty()) FaseAlertas.VACIO else FaseAlertas.LISTO,
                    mensaje = null
                )
            } catch (e: Exception) {
                val s = mutable.value
                if (s.vehiculoId != id || s.generacion != gen) return@launch
                mutable.value = s.copy(
                    fase = FaseAlertas.ERROR,
                    mensaje = e.message ?: "No se pudieron cargar las alertas"
                )
            }
        }
    }

    companion object {
        fun filtrar(lista: List<DatosAlerta>, filtro: FiltroAlertas): List<DatosAlerta> {
            val base = lista.filter { it.estado != "ANULADA" }
            val estado = filtro.estadoApi ?: return base
            return base.filter { it.estado == estado }
        }
    }
}
