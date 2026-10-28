package com.mycar.app.features.alertas.data

import retrofit2.http.*

interface AlertasApi {
    @GET("api/v1/rf22/contexto")
    suspend fun contexto(@Query("idVehiculo") idVehiculo: Long): ContextoRf22Dto

    @GET("api/v1/rf22/vehiculos/{idVehiculo}/alertas")
    suspend fun listar(@Path("idVehiculo") idVehiculo: Long): ListaAlertasDto

    @POST("api/v1/rf22/vehiculos/{idVehiculo}/alertas")
    suspend fun programar(@Path("idVehiculo") idVehiculo: Long, @Body body: ProgramarAlertaDto): ProgramarRespuestaDto
}

data class ContextoRf22Dto(
    val fechaServidor: String?, val zonaHoraria: String?, val kmActual: Int?, val idLecturaActual: Long?,
    val tipos: List<TipoAlertaDto>?, val referencias: List<ReferenciaAlertaDto>?, val programacionDisponible: Boolean?
)
data class TipoAlertaDto(val idTipoMantenimiento: Long?, val codigo: String?, val nombre: String?)
data class ReferenciaAlertaDto(val idMantenimiento: Long?, val codigoTipo: String?, val fechaServicio: String?, val servicioOtro: String?)
data class ProgramarAlertaDto(
    val idOperacion: String, val codigoTipoMantenimiento: String, val criterio: String,
    val unidadTiempo: String? = null, val cantidadTiempo: Int? = null, val intervaloKm: Int? = null,
    val servicioOtro: String? = null, val idMantenimientoReferencia: Long? = null
)
data class ProgramarRespuestaDto(val idOperacion: String?, val idAlerta: Long?, val estado: String?)
data class AlertaItemDto(
    val idAlerta: Long?, val codigoTipo: String?, val nombreTipo: String?, val criterio: String?,
    val estado: String?, val fechaObjetivo: String?, val kmObjetivo: Int?, val kmBase: Int?,
    val intervaloKm: Int?, val servicioOtro: String?, val versionFila: Int?
)
data class ListaAlertasDto(val alertasSincronizadas: Int?, val alertas: List<AlertaItemDto>?)
