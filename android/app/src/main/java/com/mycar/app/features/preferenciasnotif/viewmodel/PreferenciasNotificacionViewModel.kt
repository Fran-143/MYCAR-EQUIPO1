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
    private val solicitarPermisoOs: suspend () -> PermisoDispositivo
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoPreferenciasNotificacion())
    val estado: StateFlow<EstadoPreferenciasNotificacion> = _estado.asStateFlow()

    private var snapshotServidor: PreferenciasExternas? = null

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

    fun guardar() {
        if (_estado.value.guardando || _estado.value.cargando) return
        viewModelScope.launch {
            val antes = _estado.value
            val snapshotUi = PreferenciasExternas(
                antes.mantenimiento, antes.soat, antes.kilometraje, antes.permisoDispositivo
            )
            _estado.value = antes.copy(guardando = true, mensajeError = null, mensajeExito = null, avisoPermiso = null)
            try {
                val algunaOn = snapshotUi.mantenimiento || snapshotUi.soat || snapshotUi.kilometraje
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
                aplicar(
                    guardado,
                    avisoSiDenegado = permiso == PermisoDispositivo.DENEGADO && algunaOn,
                    exito = "Preferencias guardadas."
                )
            } catch (e: ErrorPreferencias.Sesion) {
                _estado.value = _estado.value.copy(guardando = false, sesionInvalida = true, mensajeError = e.message)
            } catch (e: Exception) {
                // EA05: restaurar switches al estado anterior (servidor o UI pre-intento).
                val revert = snapshotServidor ?: snapshotUi
                _estado.value = _estado.value.copy(
                    guardando = false,
                    mantenimiento = revert.mantenimiento,
                    soat = revert.soat,
                    kilometraje = revert.kilometraje,
                    permisoDispositivo = revert.permisoDispositivo,
                    mensajeError = "No pudimos guardar tus preferencias",
                    mensajeExito = null
                )
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
