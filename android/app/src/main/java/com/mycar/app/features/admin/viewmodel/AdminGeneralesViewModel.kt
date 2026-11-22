package com.mycar.app.features.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.admin.domain.AdminGeneralesRepository
import com.mycar.app.features.soporte.data.ContadoresIncidenciaDto
import com.mycar.app.features.soporte.data.SolicitudDetalleDto
import com.mycar.app.features.soporte.data.SolicitudResumenDto
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PantallaAdminGenerales { LISTA, DETALLE, TERMINAR, RECHAZAR, CONFIRMACION }

data class EstadoAdminGenerales(
    val pantalla: PantallaAdminGenerales = PantallaAdminGenerales.LISTA,
    val cargando: Boolean = true,
    val ocupado: Boolean = false,
    val lista: List<SolicitudResumenDto> = emptyList(),
    val contadores: ContadoresIncidenciaDto = ContadoresIncidenciaDto(),
    val filtroEstado: String? = null,
    val busqueda: String = "",
    val detalle: SolicitudDetalleDto? = null,
    val mensaje: String? = null,
    val observacionAccion: String = "",
    val textoRespuesta: String = "",
    val motivoRechazo: String = "",
    val evidenciaBytes: ByteArray? = null,
    val evidenciaCargando: Boolean = false,
    val evidenciaError: String? = null,
    val evidenciaRevelada: Boolean = false,
    val accionConfirmada: String? = null
)

class AdminGeneralesViewModel(private val repository: AdminGeneralesRepository) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoAdminGenerales())
    val estado = _estado.asStateFlow()
    private var busquedaJob: Job? = null

    init {
        cargarLista()
    }

    fun cargarLista() {
        _estado.value = _estado.value.copy(
            cargando = true,
            mensaje = null,
            pantalla = if (_estado.value.pantalla == PantallaAdminGenerales.DETALLE ||
                _estado.value.pantalla == PantallaAdminGenerales.TERMINAR ||
                _estado.value.pantalla == PantallaAdminGenerales.RECHAZAR ||
                _estado.value.pantalla == PantallaAdminGenerales.CONFIRMACION
            ) _estado.value.pantalla else PantallaAdminGenerales.LISTA
        )
        viewModelScope.launch {
            try {
                val q = _estado.value.busqueda.takeIf { it.isNotBlank() }
                val lista = repository.listar(_estado.value.filtroEstado, q)
                val contadores = repository.contadores()
                _estado.value = _estado.value.copy(cargando = false, lista = lista, contadores = contadores)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargando = false,
                    mensaje = (e as? FalloRegistroVehiculo)?.mensajeServidor ?: e.message
                )
            }
        }
    }

    fun setFiltro(estado: String?) {
        _estado.value = _estado.value.copy(filtroEstado = estado)
        cargarLista()
    }

    fun setBusqueda(q: String) {
        _estado.value = _estado.value.copy(busqueda = q)
        busquedaJob?.cancel()
        busquedaJob = viewModelScope.launch {
            delay(350)
            cargarLista()
        }
    }

    fun abrirDetalle(codigo: String) {
        _estado.value = _estado.value.copy(
            cargando = true,
            evidenciaBytes = null,
            evidenciaRevelada = false,
            evidenciaError = null,
            mensaje = null,
            observacionAccion = ""
        )
        viewModelScope.launch {
            try {
                val d = repository.detalle(codigo)
                _estado.value = _estado.value.copy(
                    cargando = false,
                    pantalla = PantallaAdminGenerales.DETALLE,
                    detalle = d
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargando = false,
                    mensaje = (e as? FalloRegistroVehiculo)?.mensajeServidor ?: e.message
                )
            }
        }
    }

    fun volverLista() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaAdminGenerales.LISTA,
            detalle = null,
            textoRespuesta = "",
            motivoRechazo = "",
            observacionAccion = "",
            evidenciaBytes = null,
            evidenciaRevelada = false,
            accionConfirmada = null
        )
        cargarLista()
    }

    fun volverDetalle() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaAdminGenerales.DETALLE,
            mensaje = null,
            textoRespuesta = "",
            motivoRechazo = ""
        )
    }

    fun editarObservacionAccion(v: String) {
        _estado.value = _estado.value.copy(
            observacionAccion = v.take(500),
            mensaje = null
        )
    }

    fun aceptar() {
        val obs = _estado.value.observacionAccion.trim().ifBlank { null }
        accion { repository.aceptar(it, obs) }
    }

    fun iniciarAtencion() {
        val obs = _estado.value.observacionAccion.trim().ifBlank { null }
        accion { repository.iniciarAtencion(it, obs) }
    }

    fun abrirTerminar() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaAdminGenerales.TERMINAR,
            textoRespuesta = "",
            mensaje = null
        )
    }

    fun abrirRechazar() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaAdminGenerales.RECHAZAR,
            motivoRechazo = "",
            mensaje = null
        )
    }

    fun editarTextoRespuesta(v: String) {
        _estado.value = _estado.value.copy(textoRespuesta = v.take(3000), mensaje = null)
    }

    fun confirmarTerminar() {
        val respuesta = _estado.value.textoRespuesta.trim()
        if (respuesta.length < 5) {
            _estado.value = _estado.value.copy(
                mensaje = "La respuesta al usuario es obligatoria (mínimo 5 caracteres)."
            )
            return
        }
        accion(irConfirmacion = true, etiqueta = "RESUELTA") { repository.terminar(it, respuesta) }
    }

    fun editarMotivoRechazo(v: String) {
        _estado.value = _estado.value.copy(
            motivoRechazo = v.take(500),
            mensaje = null
        )
    }

    fun confirmarRechazo() {
        val motivo = _estado.value.motivoRechazo.trim()
        if (motivo.length < 5) {
            _estado.value = _estado.value.copy(
                mensaje = "Indica el motivo del rechazo (mínimo 5 caracteres)."
            )
            return
        }
        accion(irConfirmacion = true, etiqueta = "RECHAZADA") {
            repository.rechazar(it, motivo)
        }
    }

    fun revelarEvidencia() {
        val codigo = _estado.value.detalle?.codigo ?: return
        if (!_estado.value.detalle!!.tieneEvidencia) return
        if (_estado.value.evidenciaBytes != null) {
            _estado.value = _estado.value.copy(evidenciaRevelada = true)
            return
        }
        _estado.value = _estado.value.copy(evidenciaCargando = true, evidenciaError = null, evidenciaRevelada = true)
        viewModelScope.launch {
            try {
                val bytes = repository.descargarEvidencia(codigo)
                _estado.value = _estado.value.copy(evidenciaCargando = false, evidenciaBytes = bytes)
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    evidenciaCargando = false,
                    evidenciaError = (e as? FalloRegistroVehiculo)?.mensajeServidor
                        ?: "No se pudo cargar el documento. Inténtalo nuevamente"
                )
            }
        }
    }

    fun ocultarEvidencia() {
        _estado.value = _estado.value.copy(evidenciaRevelada = false)
    }

    private fun accion(
        irConfirmacion: Boolean = false,
        etiqueta: String? = null,
        bloque: suspend (String) -> Unit
    ) {
        val codigo = _estado.value.detalle?.codigo ?: return
        _estado.value = _estado.value.copy(ocupado = true, mensaje = null)
        viewModelScope.launch {
            try {
                bloque(codigo)
                val d = repository.detalle(codigo)
                _estado.value = _estado.value.copy(
                    ocupado = false,
                    detalle = d,
                    pantalla = when {
                        irConfirmacion -> PantallaAdminGenerales.CONFIRMACION
                        else -> PantallaAdminGenerales.DETALLE
                    },
                    accionConfirmada = etiqueta ?: d.estado,
                    observacionAccion = ""
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    ocupado = false,
                    mensaje = (e as? FalloRegistroVehiculo)?.mensajeServidor ?: e.message
                )
            }
        }
    }

    companion object {
        fun factory(repository: AdminGeneralesRepository) = viewModelFactory {
            initializer { AdminGeneralesViewModel(repository) }
        }
    }
}