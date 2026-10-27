package com.mycar.app.features.mantenimiento.data

import java.math.BigDecimal
import retrofit2.Response
import retrofit2.http.*

/** Contrato RF17 PROPUESTO. Crear con el Retrofit autenticado compartido del equipo. */
interface MantenimientoApi {
    @GET("api/v1/rf17/contexto")
    suspend fun contexto(): Response<ContextoRf17Dto>
    @GET("api/v1/rf17/vehiculos/{vehiculo}/lecturas")
    suspend fun lecturas(@Path("vehiculo") vehiculo: Long,
                        @Query("fechaServicio") fecha: String): Response<List<LecturaRf17Dto>>
    @POST("api/v1/rf17/vehiculos/{vehiculo}/mantenimientos")
    suspend fun registrar(@Path("vehiculo") vehiculo: Long,
                          @Body solicitud: SolicitudRf17Dto): Response<ResultadoRf17Dto>
    @GET("api/v1/rf17/operaciones/{operacion}")
    suspend fun operacion(@Path("operacion") operacion: String): Response<EstadoRf17Dto>
}

data class TipoRf17Dto(val idTipoMantenimiento: Long, val codigo: String, val nombre: String)
data class VehiculoRf17Dto(
    val idVehiculo: Long, val placa: String, val marca: String, val modelo: String,
    val principal: Boolean, val idLecturaActual: Long?, val kmActual: Int?,
    val fechaLecturaActual: String?, val aceiteRegistrado: String?
)
data class LecturaRf17Dto(val idLecturaKm: Long, val valorKm: Int, val fechaLecturaUtc: String)
data class ContextoRf17Dto(
    val fechaServidor: String, val zonaHoraria: String,
    val vehiculos: List<VehiculoRf17Dto>, val tipos: List<TipoRf17Dto>,
    val registroDisponible: Boolean
)
data class RepuestoRf17Dto(val nombre: String, val marca: String?,
                          val cantidad: Int, val precioUnitario: BigDecimal?)
data class SolicitudRf17Dto(
    val idOperacion: String, val codigoTipoMantenimiento: String,
    val fechaServicio: String, val kmServicio: Int, val origenKmServicio: String,
    val idLecturaKmReferencia: Long?, val costoTotal: BigDecimal?,
    val servicioOtro: String?, val observaciones: String?,
    val proximaFechaSugerida: String?, val proximoKmSugerido: Int?,
    val repuestos: List<RepuestoRf17Dto>
)
// Respuestas con campos anulables: un JSON incompleto nunca se presenta como éxito.
data class ResultadoRf17Dto(
    val idOperacion: String?, val idMantenimiento: Long?, val estado: String?,
    val kmServicio: Int?, val fechaServicio: String?, val costoTotal: BigDecimal?,
    val cantidadRepuestos: Int?, val alertaCreada: Boolean?
)
data class EstadoRf17Dto(val idOperacion: String?, val estado: String?, val resultado: ResultadoRf17Dto?)
data class ErrorRf17Dto(val codigo: String?, val mensaje: String?,
                       val campos: Map<String,String>?, val resultadoIncierto: Boolean?)
