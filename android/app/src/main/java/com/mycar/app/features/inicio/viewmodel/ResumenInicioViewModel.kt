package com.mycar.app.features.inicio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.inicio.domain.*

class ResumenInicioViewModel(repository: ResumenInicioRepository) : ViewModel() {
    private val coordinador=CoordinadorInicio(repository,viewModelScope)
    val estado=coordinador.estado
    fun cargar() { coordinador.cargar() }
    fun seleccionar(id: Long) { coordinador.seleccionar(id) }
}
