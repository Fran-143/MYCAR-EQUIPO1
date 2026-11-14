package com.mycar.app.features.alertas.data

import retrofit2.http.*

interface AlertasApi {
    @GET("api/v1/rf22/contexto")
    suspend fun contexto(
        @Query("idVehiculo") idVehiculo: Long,
        @Query("codigoTipo") codigoTipo: String? = null,
        @Query("servicioOtro") servicioOtro: String? = null
    ): ContextoRf22Dto

    @GET("api/v1/rf22/vehiculos/{idVehiculo}/alertas")
    suspend fun listar(@Path("idVehiculo") idVehiculo: Long): ListaAlertasDto

    @GET("api/v1/rf22/vehiculos/{idVehiculo}/alertas/{idAlerta}")
    suspend fun detalle(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idAlerta") idAlerta: Long
    ): DetalleAlertaDto

    @POST("api/v1/rf22/vehiculos/{idVehiculo}/alertas")
    suspend fun programar(@Path("idVehiculo") idVehiculo: Long, @Body body: ProgramarAlertaDto): ProgramarRespuestaDto

    @GET("api/v1/rf22/operaciones/{idOperacion}")
    suspend fun operacion(@Path("idOperacion") idOperacion: String): EstadoOperacionAlertaDto

    @POST("api/v1/rf22/vehiculos/{idVehiculo}/alertas/{idAlerta}/atender")
    suspend fun atender(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idAlerta") idAlerta: Long,
        @Body body: AtenderAlertaDto
    ): AtenderAlertaRespuestaDto

    @POST("api/v1/rf22/vehiculos/{idVehiculo}/alertas/{idAlerta}/anular")
    suspend fun anular(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idAlerta") idAlerta: Long,
        @Body body: AnularAlertaDto
    ): AnularAlertaRespuestaDto

    @POST("api/v1/rf22/vehiculos/{idVehiculo}/alertas/{idAlerta}/contacto-taller")
    suspend fun elegirContactoTaller(
        @Path("idVehiculo") idVehiculo: Long,
        @Path("idAlerta") idAlerta: Long,
        @Body body: ElegirContactoTallerDto
    ): DetalleAlertaDto
}

data class ContextoRf22Dto(
    val fechaServidor: String?, val zonaHoraria: String?, val kmActual: Int?, val idLecturaActual: Long?,
    val tipos: List<TipoAlertaDto>?, val referencias: List<ReferenciaAlertaDto>?, val programacionDisponible: Boolean?
)
data class TipoAlertaDto(val idTipoMantenimiento: Long?, val codigo: String?, val nombre: String?)
data class ReferenciaAlertaDto(
    val idMantenimiento: Long?,
    val codigoTipo: String?,
    val fechaServicio: String?,
    val kmServicio: Int?,
    val servicioOtro: String?
)
data class ProgramarAlertaDto(
    val idOperacion: String,
    val codigoTipoMantenimiento: String,
    val criterio: String,
    val origenCalculo: String,
    val unidadTiempo: String? = null,
    val cantidadTiempo: Int? = null,
    val intervaloKm: Int? = null,
    val servicioOtro: String? = null,
    val idMantenimientoReferencia: Long? = null,
    val fechaBaseConfirmada: String? = null,
    val kmBaseConfirmado: Int? = null,
    val idLecturaConfirmada: Long? = null
)
data class ProgramarRespuestaDto(val idOperacion: String?, val idAlerta: Long?, val estado: String?)
data class EstadoOperacionAlertaDto(
    val idOperacion: String?,
    val estado: String?,
    val resultado: ProgramarRespuestaDto?
)
data class AlertaItemDto(
    val idAlerta: Long?, val codigoTipo: String?, val nombreTipo: String?, val criterio: String?,
    val origenCalculo: String? = null,
    val estado: String?, val fechaObjetivo: String?, val kmObjetivo: Int?, val kmBase: Int?,
    val intervaloKm: Int?, val servicioOtro: String?, val versionFila: Int?
)
data class ListaAlertasDto(val alertasSincronizadas: Int?, val alertas: List<AlertaItemDto>?)

data class VehiculoResumenAlertaDto(
    val idVehiculo: Long?, val placa: String?, val marca: String?, val modelo: String?, val estadoVehiculo: String?
)
data class AccionesDetalleAlertaDto(val atender: Boolean?, val anular: Boolean?)
data class ContactoTallerAlertaDto(
    val idTaller: Long?,
    val nombre: String?,
    val telefono: String?,
    val tieneWhatsapp: Boolean?,
    val contactoAutorizado: Boolean? = false,
    val origen: String?,
    val origenSugerencia: String?,
    val sinContactoDisponible: Boolean?
)
data class ElegirContactoTallerDto(val idTaller: Long)
data class DetalleAlertaDto(
    val idAlerta: Long?, val categoria: String?, val codigoTipo: String?, val nombreTipo: String?,
    val criterio: String?, val origenCalculo: String?, val estado: String?, val fechaEstadoUtc: String?,
    val fechaObjetivo: String?, val kmObjetivo: Int?, val kmBase: Int?, val intervaloKm: Int?,
    val unidadTiempo: String?, val cantidadTiempo: Int?, val fechaBase: String?, val servicioOtro: String?,
    val idTipoMantenimiento: Long?, val idMantenimientoReferencia: Long?, val idMantenimientoAtencion: Long?,
    val versionFila: Int?, val vehiculo: VehiculoResumenAlertaDto?, val acciones: AccionesDetalleAlertaDto?,
    val contactoTaller: ContactoTallerAlertaDto? = null
)

data class AtenderRepuestoDto(
    val nombre: String,
    val marca: String? = null,
    val cantidad: Int,
    val precioUnitario: String? = null
)

data class AtenderAlertaDto(
    val idOperacion: String,
    val fechaServicio: String,
    val kmServicio: Int,
    val origenKmServicio: String = "HISTORICO_MANUAL",
    val idLecturaKmReferencia: Long? = null,
    val costoTotal: String? = null,
    val observaciones: String? = null,
    val repuestos: List<AtenderRepuestoDto>? = null,
    val idTaller: Long? = null
)
data class AtenderAlertaRespuestaDto(
    val idAlerta: Long?,
    val estadoAlerta: String?,
    val idMantenimientoAtencion: Long?,
    val idOperacion: String?
)
data class AnularAlertaDto(
    val idOperacion: String,
    val motivo: String
)
data class AnularAlertaRespuestaDto(
    val idAlerta: Long?,
    val estado: String?,
    val idOperacion: String? = null
)
