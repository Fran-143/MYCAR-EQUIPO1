package com.mycar.app.features.alertas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.DatosAlerta
import com.mycar.app.features.alertas.domain.SolicitudAtenderAlerta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class FaseAlertas { CARGANDO, LISTO, ERROR, VACIO }

/** Ámbito RF23: consulta vehicular vs personal de licencia. */
enum class AmbitoAlertas { VEHICULOS, LICENCIA }

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
    val ambito: AmbitoAlertas = AmbitoAlertas.VEHICULOS,
    val fase: FaseAlertas = FaseAlertas.CARGANDO,
    val vehiculoId: Long? = null,
    val sincronizadas: Int = 0,
    val alertas: List<DatosAlerta> = emptyList(),
    val visibles: List<DatosAlerta> = emptyList(),
    val filtro: FiltroAlertas = FiltroAlertas.TODAS,
    val mensaje: String? = null,
    val avisoAccion: String? = null,
    val ocupadoAccion: Boolean = false,
    val generacion: Int = 0,
    /** RF23 LICENCIA: estado independiente del vehículo (RF51–56 pendiente de API). */
    val faseLicencia: FaseAlertas = FaseAlertas.VACIO,
    val mensajeLicencia: String? = MENSAJE_SIN_ALERTAS_LICENCIA,
    val alertasLicencia: List<DatosAlerta> = emptyList(),
    val generacionLicencia: Int = 0
) {
    companion object {
        const val MENSAJE_SIN_ALERTAS_LICENCIA =
            "No tienes alertas de licencia disponibles para consultar."
        const val TITULO_SIN_ALERTAS_LICENCIA = "Sin alertas de licencia"
        const val MENSAJE_SIN_VEHICULOS =
            "Necesitas un vehículo activo para consultar sus alertas."
        const val TITULO_SIN_VEHICULOS = "No hay vehículos activos disponibles"
        const val MENSAJE_SIN_ALERTAS_VEHICULO =
            "No se encontraron alertas para este vehículo."
    }
}

class AlertasViewModel(
    private val repository: AlertasRepository,
    private val vehiculos: List<Pair<Long, String>>,
    idInicial: Long?
) : ViewModel() {
    private val mutable = MutableStateFlow(
        EstadoAlertas(
            vehiculoId = idInicial ?: vehiculos.firstOrNull()?.first,
            fase = if (vehiculos.isEmpty() && idInicial == null) FaseAlertas.VACIO else FaseAlertas.CARGANDO,
            mensaje = if (vehiculos.isEmpty() && idInicial == null) EstadoAlertas.MENSAJE_SIN_VEHICULOS else null
        )
    )
    val estado = mutable.asStateFlow()

    init {
        mutable.value.vehiculoId?.let { cargar(it) }
            ?: run {
                if (vehiculos.isEmpty()) {
                    mutable.value = mutable.value.copy(
                        fase = FaseAlertas.VACIO,
                        mensaje = EstadoAlertas.MENSAJE_SIN_VEHICULOS
                    )
                }
            }
    }

    fun cambiarAmbito(ambito: AmbitoAlertas) {
        if (mutable.value.ambito == ambito) return
        mutable.value = mutable.value.copy(ambito = ambito, avisoAccion = null)
        if (ambito == AmbitoAlertas.LICENCIA) {
            cargarLicencia()
        }
    }

    fun seleccionar(id: Long) {
        // RF23: al abrir/cambiar vehículo siempre vuelve a listar (sincroniza estados tras RF14).
        val cambioVehiculo = mutable.value.vehiculoId != id
        mutable.value = mutable.value.copy(
            vehiculoId = id,
            fase = FaseAlertas.CARGANDO,
            filtro = if (cambioVehiculo) FiltroAlertas.TODAS else mutable.value.filtro,
            mensaje = null
        )
        cargar(id)
    }

    fun cambiarFiltro(filtro: FiltroAlertas) {
        val s = mutable.value
        if (s.ambito != AmbitoAlertas.VEHICULOS) return
        val visibles = filtrar(s.alertas, filtro)
        mutable.value = s.copy(
            filtro = filtro,
            visibles = visibles,
            fase = when {
                s.fase == FaseAlertas.ERROR || s.fase == FaseAlertas.CARGANDO -> s.fase
                visibles.isEmpty() -> FaseAlertas.VACIO
                else -> FaseAlertas.LISTO
            },
            mensaje = if (visibles.isEmpty() && s.fase != FaseAlertas.ERROR && s.fase != FaseAlertas.CARGANDO)
                EstadoAlertas.MENSAJE_SIN_ALERTAS_VEHICULO else s.mensaje
        )
    }

    /** G108: al volver de programar, filtro Todas. */
    fun reiniciarFiltroTodasYRecargar() {
        mutable.value = mutable.value.copy(
            ambito = AmbitoAlertas.VEHICULOS,
            filtro = FiltroAlertas.TODAS
        )
        recargar()
    }

    fun recargar() {
        when (mutable.value.ambito) {
            AmbitoAlertas.LICENCIA -> cargarLicencia()
            AmbitoAlertas.VEHICULOS -> {
                val id = mutable.value.vehiculoId ?: vehiculos.firstOrNull()?.first
                if (id == null) {
                    mutable.value = mutable.value.copy(
                        fase = FaseAlertas.VACIO,
                        mensaje = EstadoAlertas.MENSAJE_SIN_VEHICULOS,
                        alertas = emptyList(),
                        visibles = emptyList()
                    )
                    return
                }
                mutable.value = mutable.value.copy(vehiculoId = id, fase = FaseAlertas.CARGANDO, mensaje = null)
                cargar(id)
            }
        }
    }

    fun atender(idAlerta: Long, kmServicio: Int, fechaServicio: String = LocalDate.now().toString(),
                costo: String? = null, observaciones: String? = null) {
        if (mutable.value.ambito != AmbitoAlertas.VEHICULOS) return
        val vehiculo = mutable.value.vehiculoId ?: return
        if (mutable.value.ocupadoAccion || kmServicio <= 0) return
        mutable.value = mutable.value.copy(ocupadoAccion = true, avisoAccion = null)
        viewModelScope.launch {
            try {
                repository.atender(
                    vehiculo, idAlerta,
                    SolicitudAtenderAlerta(
                        idOperacion = java.util.UUID.randomUUID().toString(),
                        fechaServicio = fechaServicio,
                        kmServicio = kmServicio,
                        costoTotal = costo,
                        observaciones = observaciones
                    )
                )
                mutable.value = mutable.value.copy(ocupadoAccion = false, avisoAccion = "Alerta atendida")
                if (mutable.value.ambito == AmbitoAlertas.VEHICULOS) recargar()
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    ocupadoAccion = false,
                    avisoAccion = e.message ?: "No se pudo atender la alerta"
                )
            }
        }
    }

    fun anular(idAlerta: Long, motivo: String) {
        if (mutable.value.ambito != AmbitoAlertas.VEHICULOS) return
        val vehiculo = mutable.value.vehiculoId ?: return
        if (mutable.value.ocupadoAccion) return
        val motivoOk = motivo.trim()
        if (motivoOk.isEmpty()) {
            mutable.value = mutable.value.copy(avisoAccion = "Indica el motivo de la anulación.")
            return
        }
        if (motivoOk.length > 48) {
            mutable.value = mutable.value.copy(avisoAccion = "El motivo no puede superar 48 caracteres.")
            return
        }
        mutable.value = mutable.value.copy(ocupadoAccion = true, avisoAccion = null)
        viewModelScope.launch {
            try {
                repository.anular(vehiculo, idAlerta, motivoOk, "")
                mutable.value = mutable.value.copy(ocupadoAccion = false, avisoAccion = "Alerta anulada")
                if (mutable.value.ambito == AmbitoAlertas.VEHICULOS) recargar()
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    ocupadoAccion = false,
                    avisoAccion = e.message ?: "No se pudo anular la alerta"
                )
            }
        }
    }

    /**
     * RF23 LICENCIA: sin API de licencia en el MVP (RF51–56).
     * Empty-state real EA06; no inventa tarjetas ni consulta por idVehiculo.
     */
    private fun cargarLicencia() {
        val gen = mutable.value.generacionLicencia + 1
        mutable.value = mutable.value.copy(
            generacionLicencia = gen,
            faseLicencia = FaseAlertas.CARGANDO,
            mensajeLicencia = null
        )
        viewModelScope.launch {
            // Placeholder: cuando exista endpoint personal, cablear aquí sin idVehiculo.
            val s = mutable.value
            if (s.generacionLicencia != gen || s.ambito != AmbitoAlertas.LICENCIA) return@launch
            mutable.value = s.copy(
                faseLicencia = FaseAlertas.VACIO,
                alertasLicencia = emptyList(),
                mensajeLicencia = EstadoAlertas.MENSAJE_SIN_ALERTAS_LICENCIA
            )
        }
    }

    private fun cargar(id: Long) {
        val gen = mutable.value.generacion + 1
        mutable.value = mutable.value.copy(generacion = gen)
        viewModelScope.launch {
            try {
                val (sync, lista) = repository.listar(id)
                val s = mutable.value
                // Anti-carrera: ignorar si cambió vehículo o llegó otra generación.
                if (s.vehiculoId != id || s.generacion != gen) return@launch
                val sinAnuladas = lista.filter { it.estado != "ANULADA" }
                val visibles = filtrar(sinAnuladas, s.filtro)
                mutable.value = s.copy(
                    sincronizadas = sync,
                    alertas = sinAnuladas,
                    visibles = visibles,
                    fase = if (visibles.isEmpty()) FaseAlertas.VACIO else FaseAlertas.LISTO,
                    mensaje = if (visibles.isEmpty()) EstadoAlertas.MENSAJE_SIN_ALERTAS_VEHICULO else null
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
