package com.mycar.app.features.recordatorioskm.domain

import java.math.BigDecimal

enum class FrecuenciaKm(val api: String, val etiqueta: String) {
    DIARIO("DIARIO", "Diario"),
    INTERDIARIO("INTERDIARIO", "Interdiario"),
    SEMANAL("SEMANAL", "Semanal"),
    QUINCENAL("QUINCENAL", "Quincenal"),
    MENSUAL("MENSUAL", "Mensual");

    companion object {
        fun fromApi(raw: String?): FrecuenciaKm =
            entries.firstOrNull { it.api.equals(raw?.trim(), ignoreCase = true) } ?: INTERDIARIO
    }
}

data class ConfiguracionRecordatoriosKm(
    val activo: Boolean,
    val vehiculos: List<VehiculoRecordatorioKm>
)

data class VehiculoRecordatorioKm(
    val idVehiculo: Long,
    val marca: String,
    val modelo: String,
    val placa: String,
    val tieneFoto: Boolean,
    val kilometrajeActual: BigDecimal?,
    val frecuencia: FrecuenciaKm,
    val proximoRecordatorio: String?,
    val sinLecturaValida: Boolean,
    val pendiente: Boolean
)

sealed class ErrorRecordatorioKm : Exception() {
    class Sesion(override val message: String) : ErrorRecordatorioKm()
    class Negocio(val codigo: String, override val message: String) : ErrorRecordatorioKm()
    class Red(override val message: String) : ErrorRecordatorioKm()
}

interface RecordatoriosKmRepository {
    suspend fun consultar(): ConfiguracionRecordatoriosKm
    suspend fun actualizarGlobal(activo: Boolean): ConfiguracionRecordatoriosKm
    suspend fun guardarFrecuencia(idVehiculo: Long, frecuencia: FrecuenciaKm): VehiculoRecordatorioKm
}
