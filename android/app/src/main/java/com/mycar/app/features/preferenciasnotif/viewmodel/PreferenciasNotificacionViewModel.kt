package com.mycar.app.features.preferenciasnotif.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.preferenciasnotif.domain.ErrorPreferencias
import com.mycar.app.features.preferenciasnotif.domain.PermisoDispositivo
import com.mycar.app.features.preferenciasnotif.domain.PreferenciasExternas
import com.mycar.app.features.preferenciasnotif.domain.PreferenciasNotificacionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EstadoPreferenciasNotificacion(
    val cargando: Boolean = true,
    val guardando: Boolean = false,
    val mantenimiento: Boolean = true,
    val soat: Boolean = true,
    val kilometraje: Boolean = true,
    val permisoDispositivo: PermisoDispositivo = PermisoDispositivo.NO_SOLICITADO,
    val avisoPermiso: String? = null,
    val mensajeExito: String? = null,
    val mensajeError: String? = null,
    val sesionInvalida: Boolean = false
)

/**
 * RF48. [consultarPermisoOs] / [solicitarPermisoOs] los provee la UI (Activity).
 * Si alguna categoría queda ON y el permiso no está concedido, se solicita al guardar.
 */
class PreferenciasNotificacionViewModel(
    private val repository: PreferenciasNotificacionRepository,
    private val consultarPermisoOs: () -> PermisoDispositivo,
    private val solicitarPermisoOs: suspend () -> PermisoDispositivo,
    /** Tras conceder OS + guardar ON: re-registrar FCM para que llegue el push. */
    private val onPermisoConcedidoParaPush: (suspend () -> Unit)? = null
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoPreferenciasNotificacion())
    val estado: StateFlow<EstadoPreferenciasNotificacion> = _estado.asStateFlow()

    private var snapshotServidor: PreferenciasExternas? = null
    /** Si el usuario toca otro switch mientras hay un guardar en curso, reintentar al terminar. */
    private var guardarPendiente = false

    init { cargar() }

    fun cargar() {
        viewModelScope.launch {
            _estado.value = _estado.value.copy(cargando = true, mensajeError = null, mensajeExito = null)
            try {
                val prefs = repository.consultar()
                snapshotServidor = prefs
                val os = consultarPermisoOs()
                val reconciliado = reconciliarPermisoAlAbrir(prefs, os)
                aplicar(reconciliado, avisoSiDenegado = os == PermisoDispositivo.DENEGADO)
            } catch (e: ErrorPreferencias.Sesion) {
                _estado.value = _estado.value.copy(cargando = false, sesionInvalida = true, mensajeError = e.message)
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargando = false,
                    mensajeError = e.message ?: "No pudimos cargar tus preferencias."
                )
            }
        }
    }

    fun setMantenimiento(v: Boolean) = editar { it.copy(mantenimiento = v, mensajeError = null, mensajeExito = null) }
    fun setSoat(v: Boolean) = editar { it.copy(soat = v, mensajeError = null, mensajeExito = null) }
    fun setKilometraje(v: Boolean) = editar { it.copy(kilometraje = v, mensajeError = null, mensajeExito = null) }

    /** Switch UI: aplica y guarda de inmediato (antes el usuario debía pulsar Guardar). */
    fun cambiarMantenimiento(v: Boolean) {
        setMantenimiento(v)
        guardar()
    }

    fun cambiarSoat(v: Boolean) {
        setSoat(v)
        guardar()
    }

    fun guardar() {
        if (_estado.value.cargando) {
            guardarPendiente = true
            return
        }
        if (_estado.value.guardando) {
            // Conservar el valor del switch en UI; al terminar el guardar actual, reenviar.
            guardarPendiente = true
            return
        }
        viewModelScope.launch {
            val antes = _estado.value
            val snapshotUi = PreferenciasExternas(
                antes.mantenimiento, antes.soat, antes.kilometraje, antes.permisoDispositivo
            )
            _estado.value = antes.copy(guardando = true, mensajeError = null, mensajeExito = null, avisoPermiso = null)
            try {
                // Kilometraje ya no se configura aquí (RF43); solo mantenimiento/SOAT piden permiso OS.
                val algunaOn = snapshotUi.mantenimiento || snapshotUi.soat
                val permiso = if (!algunaOn) {
                    // EA01: no solicitar permiso nuevo; conservar el almacenado/OS actual.
                    consultarPermisoOs().takeIf { it != PermisoDispositivo.NO_SOLICITADO }
                        ?: snapshotUi.permisoDispositivo
                } else {
                    val actualOs = consultarPermisoOs()
                    if (actualOs == PermisoDispositivo.CONCEDIDO) {
                        actualOs // EA03
                    } else {
                        solicitarPermisoOs() // EA02 o concesión
                    }
                }
                val guardado = repository.guardar(
                    snapshotUi.mantenimiento, snapshotUi.soat, snapshotUi.kilometraje, permiso
                )
                snapshotServidor = guardado
                // Si hubo otro toggle mientras guardábamos, no pisar la UI con la respuesta vieja.
                val uiAhora = _estado.value
                val huboCambioLocal = uiAhora.mantenimiento != snapshotUi.mantenimiento
                    || uiAhora.soat != snapshotUi.soat
                    || uiAhora.kilometraje != snapshotUi.kilometraje
                if (huboCambioLocal || guardarPendiente) {
                    _estado.value = uiAhora.copy(
                        guardando = false,
                        permisoDispositivo = permiso,
                        avisoPermiso = if (permiso == PermisoDispositivo.DENEGADO &&
                            (uiAhora.mantenimiento || uiAhora.soat)
                        ) {
                            "Activa los permisos de notificación del dispositivo para recibir avisos externos"
                        } else null,
                        mensajeExito = null,
                        mensajeError = null
                    )
                } else {
                    aplicar(
                        guardado,
                        avisoSiDenegado = permiso == PermisoDispositivo.DENEGADO && algunaOn,
                        exito = "Preferencias guardadas. Los push usan estos interruptores."
                    )
                }
                // Con permiso OS concedido, asegurar token FCM activo (segundo celular / amiga).
                if (permiso == PermisoDispositivo.CONCEDIDO && algunaOn) {
                    onPermisoConcedidoParaPush?.invoke()
                }
            } catch (e: ErrorPreferencias.Sesion) {
                _estado.value = _estado.value.copy(guardando = false, sesionInvalida = true, mensajeError = e.message)
                guardarPendiente = false
            } catch (e: Exception) {
                // EA05: restaurar switches al estado anterior (servidor o UI pre-intento),
                // salvo si el usuario ya cambió otro switch (queda pendiente de reintento).
                if (guardarPendiente) {
                    _estado.value = _estado.value.copy(
                        guardando = false,
                        mensajeError = e.message?.takeIf { it.isNotBlank() }
                            ?: "No pudimos guardar tus preferencias",
                        mensajeExito = null
                    )
                } else {
                    val revert = snapshotServidor ?: snapshotUi
                    _estado.value = _estado.value.copy(
                        guardando = false,
                        mantenimiento = revert.mantenimiento,
                        soat = revert.soat,
                        kilometraje = revert.kilometraje,
                        permisoDispositivo = revert.permisoDispositivo,
                        mensajeError = e.message?.takeIf { it.isNotBlank() }
                            ?: "No pudimos guardar tus preferencias",
                        mensajeExito = null
                    )
                }
            }
            if (guardarPendiente && !_estado.value.sesionInvalida) {
                guardarPendiente = false
                guardar()
            }
        }
    }

    private suspend fun reconciliarPermisoAlAbrir(
        prefs: PreferenciasExternas,
        os: PermisoDispositivo
    ): PreferenciasExternas {
        // EA04: OS DENEGADO y backend aún CONCEDIDO → actualizar solo permiso.
        if (os == PermisoDispositivo.DENEGADO && prefs.permisoDispositivo != PermisoDispositivo.DENEGADO) {
            return try {
                repository.actualizarPermisoDispositivo(PermisoDispositivo.DENEGADO).also {
                    snapshotServidor = it
                }
            } catch (_: Exception) {
                prefs.copy(permisoDispositivo = PermisoDispositivo.DENEGADO)
            }
        }
        if (os == PermisoDispositivo.CONCEDIDO && prefs.permisoDispositivo != PermisoDispositivo.CONCEDIDO) {
            return try {
                repository.actualizarPermisoDispositivo(PermisoDispositivo.CONCEDIDO).also {
                    snapshotServidor = it
                }
            } catch (_: Exception) {
                prefs.copy(permisoDispositivo = PermisoDispositivo.CONCEDIDO)
            }
        }
        return prefs
    }

    private fun aplicar(
        prefs: PreferenciasExternas,
        avisoSiDenegado: Boolean = false,
        exito: String? = null
    ) {
        _estado.value = EstadoPreferenciasNotificacion(
            cargando = false,
            guardando = false,
            mantenimiento = prefs.mantenimiento,
            soat = prefs.soat,
            kilometraje = prefs.kilometraje,
            permisoDispositivo = prefs.permisoDispositivo,
            avisoPermiso = if (avisoSiDenegado || prefs.permisoDispositivo == PermisoDispositivo.DENEGADO
                && (prefs.mantenimiento || prefs.soat || prefs.kilometraje)
            ) {
                "Activa los permisos de notificación del dispositivo para recibir avisos externos"
            } else null,
            mensajeExito = exito,
            mensajeError = null
        )
    }

    private fun editar(transform: (EstadoPreferenciasNotificacion) -> EstadoPreferenciasNotificacion) {
        _estado.value = transform(_estado.value)
    }
}
