package com.mycar.app.features.vehiculos.data

import com.google.gson.Gson
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import com.mycar.app.features.vehiculos.domain.ListadoVehiculosRepository

class RemoteListadoVehiculosRepository(private val api: ListadoVehiculosApi) : ListadoVehiculosRepository {
    private data class ErrorApi(val codigo: String?)
    override suspend fun listar(filtro: String, busqueda: String): ListadoVehiculosDto {
        val r = api.listar(filtro, busqueda)
        if (!r.isSuccessful) {
            val codigo = runCatching { r.errorBody()?.use { Gson().fromJson(it.string(), ErrorApi::class.java)?.codigo } }.getOrNull()
            throw FalloRegistroVehiculo(codigo ?: if (r.code() == 401) "SESION_NO_VALIDA" else "ERROR_HTTP", r.code())
        }
        val listado = r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
        val valido = runCatching {
            val c = listado.resumen
            c.todos >= 0 && c.activos >= 0 && c.inactivos >= 0 && c.activos + c.inactivos == c.todos &&
                listado.filtro == filtro && listado.busqueda == busqueda.trim().replace(Regex("\\s+"), " ") &&
                listado.vehiculos.size.toLong() <= c.todos &&
                listado.vehiculos.map { it.idVehiculo }.distinct().size == listado.vehiculos.size &&
                listado.vehiculos.all {
                    it.idVehiculo > 0 && it.placa.isNotBlank() && it.marca.isNotBlank() && it.modelo.isNotBlank() &&
                        it.estadoVehiculo in setOf("ACTIVO", "INACTIVO") &&
                        (filtro == "TODOS" || it.estadoVehiculo == filtro) &&
                        (it.kilometrajeActual == null || it.kilometrajeActual > 0)
                }
        }.getOrDefault(false)
        if (!valido) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
        return listado
    }
}
