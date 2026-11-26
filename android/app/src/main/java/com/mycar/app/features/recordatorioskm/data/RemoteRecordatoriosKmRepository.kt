package com.mycar.app.features.recordatorioskm.data

import com.google.gson.Gson
import com.mycar.app.features.recordatorioskm.domain.ConfiguracionRecordatoriosKm
import com.mycar.app.features.recordatorioskm.domain.ErrorRecordatorioKm
import com.mycar.app.features.recordatorioskm.domain.FrecuenciaKm
import com.mycar.app.features.recordatorioskm.domain.RecordatoriosKmRepository
import com.mycar.app.features.recordatorioskm.domain.VehiculoRecordatorioKm
import retrofit2.Response

class RemoteRecordatoriosKmRepository(
    private val api: RecordatoriosKmApi,
    private val gson: Gson = Gson()
) : RecordatoriosKmRepository {

    override suspend fun consultar(): ConfiguracionRecordatoriosKm {
        return mapConfig(ejecutar { api.consultar() })
    }

    override suspend fun actualizarGlobal(activo: Boolean): ConfiguracionRecordatoriosKm {
        val estado = if (activo) "ACTIVO" else "PAUSADO"
        return mapConfig(ejecutar { api.actualizarGlobal(EstadoGlobalRequestDto(estado)) })
    }

    override suspend fun guardarFrecuencia(idVehiculo: Long, frecuencia: FrecuenciaKm): VehiculoRecordatorioKm {
        val dto = ejecutar { api.guardarFrecuencia(idVehiculo, FrecuenciaRequestDto(frecuencia.api)) }
        return VehiculoRecordatorioKm(
            idVehiculo = dto.idVehiculo,
            marca = "",
            modelo = "",
            placa = "",
            tieneFoto = false,
            kilometrajeActual = null,
            frecuencia = FrecuenciaKm.fromApi(dto.frecuencia),
            proximoRecordatorio = dto.proximoRecordatorio,
            sinLecturaValida = dto.sinLecturaValida,
            pendiente = dto.pendiente
        )
    }

    private fun mapConfig(dto: ConfiguracionRecordatoriosKmDto): ConfiguracionRecordatoriosKm {
        val activo = dto.estadoRecordatorios.equals("ACTIVO", ignoreCase = true)
        val vehiculos = (dto.vehiculos ?: emptyList()).map { v ->
            VehiculoRecordatorioKm(
                idVehiculo = v.idVehiculo,
                marca = v.marca.orEmpty(),
                modelo = v.modelo.orEmpty(),
                placa = v.placa.orEmpty(),
                tieneFoto = v.tieneFoto,
                kilometrajeActual = v.kilometrajeActual,
                frecuencia = FrecuenciaKm.fromApi(v.frecuencia),
                proximoRecordatorio = v.proximoRecordatorio,
                sinLecturaValida = v.sinLecturaValida,
                pendiente = v.pendiente
            )
        }
        return ConfiguracionRecordatoriosKm(activo, vehiculos)
    }

    private suspend fun <T> ejecutar(bloque: suspend () -> Response<T>): T {
        val resp = try {
            bloque()
        } catch (e: Exception) {
            throw ErrorRecordatorioKm.Red(e.message ?: "No se pudo conectar con el servidor.")
        }
        if (resp.code() == 401) {
            throw ErrorRecordatorioKm.Sesion("Sesión no válida.")
        }
        if (!resp.isSuccessful) {
            val err = resp.errorBody()?.string()?.let {
                runCatching { gson.fromJson(it, ErrorRecordatorioKmDto::class.java) }.getOrNull()
            }
            throw ErrorRecordatorioKm.Negocio(
                err?.codigo ?: "ERROR",
                err?.mensaje ?: "No se pudo completar la operación."
            )
        }
        return resp.body() ?: throw ErrorRecordatorioKm.Red("Respuesta vacía del servidor.")
    }
}
