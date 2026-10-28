package com.mycar.app.features.alertas.programacion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.alertas.programacion.domain.*
import com.mycar.app.features.autenticacion.data.AuthRepository
import com.mycar.app.features.autenticacion.data.SessionPhase
import kotlinx.coroutines.launch

/** Estado del módulo delimitado por la sesión real de Elsa. No contiene contraseñas ni tokens. */
class ProgramacionViewModel(private val sesiones: AuthRepository, repository: ProgramacionRepository,
    archivos: (String) -> PendientesProgramacion) : ViewModel() {
    val coordinador = CoordinadorProgramacion(repository, archivos)
    private val contextoInicial = sesiones.session.value.contextVersion
    private var retirado = false
    init {
        viewModelScope.launch {
            sesiones.session.collect { estado ->
                if (estado.contextVersion != contextoInicial) retirar(true)
                else if (estado.phase == SessionPhase.SIGNED_OUT) retirar(false)
            }
        }
        viewModelScope.launch { coordinador.iniciar() }
    }
    fun ejecutar(accion: suspend CoordinadorProgramacion.() -> Unit) {
        if (!retirado) viewModelScope.launch { coordinador.accion() }
    }
    fun retirar(borrar: Boolean) {
        // Una instancia de una sesión anterior nunca borra pendientes de la sesión siguiente.
        if (!retirado) { retirado = true; coordinador.cerrar(borrar) }
    }
    override fun onCleared() { retirar(false); super.onCleared() }
    class Factory(private val sesiones: AuthRepository, private val repository: ProgramacionRepository,
        private val archivos: (String) -> PendientesProgramacion) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProgramacionViewModel::class.java))
            return ProgramacionViewModel(sesiones, repository, archivos) as T
        }
    }
}
