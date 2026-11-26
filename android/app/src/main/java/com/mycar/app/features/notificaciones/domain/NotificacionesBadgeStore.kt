package com.mycar.app.features.notificaciones.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contador global de notificaciones NO LEÍDAS para el badge de la campana.
 * Una sola fuente; lo actualiza la bandeja / login / sincronización.
 */
object NotificacionesBadgeStore {
    private val _noLeidas = MutableStateFlow(0)
    val noLeidas: StateFlow<Int> = _noLeidas.asStateFlow()

    fun publicar(cantidad: Int) {
        _noLeidas.value = cantidad.coerceAtLeast(0)
    }

    fun reiniciar() {
        _noLeidas.value = 0
    }
}
