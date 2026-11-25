package com.mycar.app.features.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.admin.data.AdminDecisionDto
import com.mycar.app.features.admin.data.AdminSolicitudDetalleDto
import com.mycar.app.features.admin.data.AdminSolicitudItemDto
import com.mycar.app.features.admin.data.AdminSuscripcionDetalleDto
import com.mycar.app.features.admin.data.AdminSuscripcionItemDto
import com.mycar.app.features.admin.data.AdminSuscripcionesRepository
import com.mycar.app.features.admin.data.AdminSuscripcionesResumenDto
import com.mycar.app.features.admin.data.AdminVerificarDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PestanaAdminSuscripciones { SOLICITUDES, SUSCRIPCIONES }

enum class PantallaAdminSuscripciones { LISTA, DETALLE_SUSCRIPCION, DETALLE_SOLICITUD }

data class EstadoAdminSuscripciones(
    val pantalla: PantallaAdminSuscripciones = PantallaAdminSuscripciones.LISTA,
    val pestana: PestanaAdminSuscripciones = PestanaAdminSuscripciones.SOLICITUDES,
    val cargando: Boolean = false,
    val mensaje: String? = null,
    val resumen: AdminSuscripcionesResumenDto? = null,
    val items: List<AdminSuscripcionItemDto> = emptyList(),
    val solicitudes: List<AdminSolicitudItemDto> = emptyList(),
    val filtro: String? = null,
    val q: String = "",
    val detalle: AdminSuscripcionDetalleDto? = null,
    val detalleSolicitud: AdminSolicitudDetalleDto? = null,
    val verificacion: AdminVerificarDto? = null,
    val decision: AdminDecisionDto? = null,
    val mostrarAprobar: Boolean = false,
    val mostrarRechazar: Boolean = false,
    val motivoRechazo: String = "",
    val mostrarCancelarPremium: Boolean = false,
    val motivoCancelarPremium: String = ""
)

class SuscripcionesAdminViewModel(
    private val repository: AdminSuscripcionesRepository
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoAdminSuscripciones(cargando = true))
    val estado: StateFlow<EstadoAdminSuscripciones> = _estado.asStateFlow()
    private var debounceJob: Job? = null

    init { refrescar() }

    fun setPestana(p: PestanaAdminSuscripciones) {
        _estado.value = _estado.value.copy(pestana = p, filtro = null, q = "")
        refrescar()
    }

    fun refrescar() {
        viewModelScope.launch {
            _estado.value = _estado.value.copy(cargando = true, mensaje = null)
            try {
                val resumen = repository.resumen()
                val q = _estado.value.q.ifBlank { null }
                if (_estado.value.pestana == PestanaAdminSuscripciones.SOLICITUDES) {
                    val sols = repository.listarSolicitudes(_estado.value.filtro, q)
                    _estado.value = _estado.value.copy(
                        cargando = false, resumen = resumen, solicitudes = sols
                    )
                } else {
                    val items = repository.listar(_estado.value.filtro, q)
                    _estado.value = _estado.value.copy(
                        cargando = false, resumen = resumen, items = items
                    )
                }
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargando = false,
                    mensaje = e.message ?: "No se pudo cargar suscripciones."
                )
            }
        }
    }

    fun setFiltro(filtro: String?) {
        _estado.value = _estado.value.copy(filtro = filtro)
        refrescar()
    }

    fun setQuery(q: String) {
        _estado.value = _estado.value.copy(q = q)
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(350)
            refrescar()
        }
    }

    fun limpiarQuery() {
        _estado.value = _estado.value.copy(q = "")
        refrescar()
    }

    fun abrirDetalleUsuario(idUsuario: Long) {
        viewModelScope.launch {
            _estado.value = _estado.value.copy(
                pantalla = PantallaAdminSuscripciones.DETALLE_SUSCRIPCION,
                cargando = true, verificacion = null, mensaje = null
            )
            try {
                val d = repository.detalle(idUsuario)
                _estado.value = _estado.value.copy(cargando = false, detalle = d)
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargando = false, mensaje = e.message ?: "No se pudo cargar el detalle."
                )
            }
        }
    }

    fun abrirDetalleSolicitud(codigo: String) {
        viewModelScope.launch {
            _estado.value = _estado.value.copy(
                pantalla = PantallaAdminSuscripciones.DETALLE_SOLICITUD,
                cargando = true, decision = null, mensaje = null,
                mostrarAprobar = false, mostrarRechazar = false, motivoRechazo = ""
            )
            try {
                val d = repository.detalleSolicitud(codigo)
                _estado.value = _estado.value.copy(cargando = false, detalleSolicitud = d)
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargando = false, mensaje = e.message ?: "No se pudo cargar la solicitud."
                )
            }
        }
    }

    fun verificarUltima() {
        val ref = _estado.value.detalle?.ultimaOperacion?.referencia
            ?: _estado.value.detalleSolicitud?.solicitud?.referenciaOperacion
            ?: return
        viewModelScope.launch {
            try {
                val v = repository.verificar(ref)
                _estado.value = _estado.value.copy(verificacion = v, mensaje = null)
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(mensaje = e.message ?: "No se pudo verificar.")
            }
        }
    }

    fun pedirAprobar() { _estado.value = _estado.value.copy(mostrarAprobar = true) }
    fun pedirRechazar() { _estado.value = _estado.value.copy(mostrarRechazar = true) }
    fun pedirCancelarPremium() {
        _estado.value = _estado.value.copy(mostrarCancelarPremium = true, motivoCancelarPremium = "")
    }
    fun cancelarModales() {
        _estado.value = _estado.value.copy(
            mostrarAprobar = false, mostrarRechazar = false, mostrarCancelarPremium = false
        )
    }

    fun setMotivoRechazo(v: String) {
        _estado.value = _estado.value.copy(motivoRechazo = v)
    }

    fun setMotivoCancelarPremium(v: String) {
        _estado.value = _estado.value.copy(motivoCancelarPremium = v)
    }

    fun confirmarCancelarPremium() {
        val id = _estado.value.detalle?.idUsuario
            ?: _estado.value.detalleSolicitud?.idUsuario
            ?: return
        val motivo = _estado.value.motivoCancelarPremium.trim()
        if (motivo.isEmpty()) {
            _estado.value = _estado.value.copy(mensaje = "El motivo de anulación es obligatorio.")
            return
        }
        viewModelScope.launch {
            try {
                val d = repository.cancelarPremium(id, motivo)
                val desdeSolicitud = _estado.value.pantalla == PantallaAdminSuscripciones.DETALLE_SOLICITUD
                if (desdeSolicitud) {
                    val codigo = _estado.value.detalleSolicitud?.solicitud?.codigo
                    val detSol = codigo?.let { runCatching { repository.detalleSolicitud(it) }.getOrNull() }
                    _estado.value = _estado.value.copy(
                        decision = d,
                        detalleSolicitud = detSol ?: _estado.value.detalleSolicitud,
                        mostrarCancelarPremium = false,
                        mensaje = d.mensaje ?: "Premium anulado. Usuario en FREE."
                    )
                } else {
                    val det = repository.detalle(id)
                    _estado.value = _estado.value.copy(
                        decision = d, detalle = det,
                        mostrarCancelarPremium = false, mensaje = d.mensaje
                    )
                }
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    mostrarCancelarPremium = false,
                    mensaje = e.message ?: "No se pudo anular Premium."
                )
            }
        }
    }

    fun confirmarAprobar() {
        val codigo = _estado.value.detalleSolicitud?.solicitud?.codigo ?: return
        viewModelScope.launch {
            try {
                val d = repository.aprobar(codigo, null)
                val det = repository.detalleSolicitud(codigo)
                _estado.value = _estado.value.copy(
                    decision = d, detalleSolicitud = det,
                    mostrarAprobar = false, mensaje = d.mensaje
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    mostrarAprobar = false,
                    mensaje = e.message ?: "No se pudo aprobar."
                )
            }
        }
    }

    fun confirmarRechazar() {
        val codigo = _estado.value.detalleSolicitud?.solicitud?.codigo ?: return
        val motivo = _estado.value.motivoRechazo.trim()
        if (motivo.isEmpty()) {
            _estado.value = _estado.value.copy(mensaje = "El motivo del rechazo es obligatorio.")
            return
        }
        viewModelScope.launch {
            try {
                val d = repository.rechazar(codigo, motivo)
                val det = repository.detalleSolicitud(codigo)
                _estado.value = _estado.value.copy(
                    decision = d, detalleSolicitud = det,
                    mostrarRechazar = false, mensaje = d.mensaje
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    mostrarRechazar = false,
                    mensaje = e.message ?: "No se pudo rechazar."
                )
            }
        }
    }

    fun volverLista() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaAdminSuscripciones.LISTA,
            detalle = null, detalleSolicitud = null, verificacion = null, decision = null,
            mostrarAprobar = false, mostrarRechazar = false, mostrarCancelarPremium = false
        )
        refrescar()
    }
}
