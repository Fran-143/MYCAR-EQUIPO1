package com.mycar.app.features.vehiculos.domain

import com.mycar.app.features.vehiculos.data.ListadoVehiculosDto

interface ListadoVehiculosRepository {
    suspend fun listar(filtro: String, busqueda: String): ListadoVehiculosDto
}
