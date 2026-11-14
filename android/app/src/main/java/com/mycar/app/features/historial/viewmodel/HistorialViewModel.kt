package com.mycar.app.features.historial.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.historial.domain.CoordinadorHistorial
import com.mycar.app.features.historial.domain.FuenteLecturasKilometraje
import com.mycar.app.features.historial.domain.HistorialRepository
import kotlinx.coroutines.launch

class HistorialViewModel(
    repository: HistorialRepository,
    private val idInicial: Long?,
    lecturasKm: FuenteLecturasKilometraje? = null
) : ViewModel() {
    private val coordinador = CoordinadorHistorial(repository, lecturasKm)
    val estado = coordinador.estado
    fun recargar() { viewModelScope.launch { coordinador.cargar(estado.value.seleccionado?.id ?: idInicial) } }
    fun seleccionar(id: Long) { viewModelScope.launch { coordinador.seleccionar(id) } }
    fun reintentar() { viewModelScope.launch { coordinador.reintentar() } }
    fun filtrarTipo(codigo: String?) { coordinador.filtrarTipo(codigo) }
    fun ordenar(recientes: Boolean) { coordinador.ordenar(recientes) }
    fun limpiarFiltros() { coordinador.limpiarFiltros() }
    fun recargarKilometraje() { viewModelScope.launch { coordinador.cargarKilometraje() } }
}
