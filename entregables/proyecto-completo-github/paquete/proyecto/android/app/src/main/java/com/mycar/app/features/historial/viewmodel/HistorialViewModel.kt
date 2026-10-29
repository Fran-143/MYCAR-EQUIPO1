package com.mycar.app.features.historial.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.historial.domain.CoordinadorHistorial
import com.mycar.app.features.historial.domain.HistorialRepository
import kotlinx.coroutines.launch

class HistorialViewModel(repository: HistorialRepository, private val idInicial: Long?) : ViewModel() {
    private val coordinador = CoordinadorHistorial(repository)
    val estado = coordinador.estado
    fun recargar() { viewModelScope.launch { coordinador.cargar(estado.value.seleccionado?.id ?: idInicial) } }
    fun seleccionar(id: Long) { viewModelScope.launch { coordinador.seleccionar(id) } }
    fun reintentar() { viewModelScope.launch { coordinador.reintentar() } }
}
