package com.mycar.app.features.admin.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class AdminSuscripcionesResumenDto(
    val freeActivas: Long = 0,
    val premiumPruebaActivas: Long = 0,
    val premiumSandboxActivas: Long = 0,
    val vencidas: Long = 0,
    val solicitudesPendientesAprobacion: Long = 0,
    val operacionesPendientes: Long = 0,
    val operacionesFinalizadas: Long = 0,
    val nota: String? = null
)

data class AdminSuscripcionItemDto(
    val idUsuario: Long = 0,
    val correo: String? = null,
    val nombre: String? = null,
    val planCodigo: String? = null,
    val modalidad: String? = null,
    val estado: String? = null,
    val origen: String? = null,
    val inicioUtc: String? = null,
    val finUtc: String? = null,
    val etiquetaSandbox: Boolean = false,
    val diasRestantes: Int? = null
)

data class AdminSuscripcionesPageDto(val items: List<AdminSuscripcionItemDto> = emptyList())

data class AdminSolicitudItemDto(
    val codigo: String? = null,
    val idUsuario: Long = 0,
    val correo: String? = null,
    val nombre: String? = null,
    val modalidad: String? = null,
    val estado: String? = null,
    val creadoUtc: String? = null,
    val referenciaOperacion: String? = null,
    val proveedorOperacion: String? = null,
    val estadoOperacion: String? = null
)

data class AdminSolicitudesPageDto(val items: List<AdminSolicitudItemDto> = emptyList())

data class AdminSolicitudDetalleDto(
    val solicitud: com.mycar.app.features.planes.data.SolicitudSuscripcionDto? = null,
    val idUsuario: Long = 0,
    val correo: String? = null,
    val nombre: String? = null,
    val vigenciaQueSeConcederia: String? = null,
    val cupoVehiculos: Int? = null,
    val descripcionBeneficios: String? = null,
    val puedeAprobar: Boolean = false,
    val motivoNoAprobar: String? = null,
    val historial: List<com.mycar.app.features.planes.data.EventoSuscripcionDto> = emptyList(),
    val solicitudesAnteriores: List<AdminSolicitudItemDto> = emptyList()
)

data class AdminSuscripcionDetalleDto(
    val idUsuario: Long = 0,
    val correo: String? = null,
    val nombre: String? = null,
    val estadoCuenta: String? = null,
    val suscripcion: com.mycar.app.features.planes.data.MiSuscripcionDto? = null,
    val eventos: List<com.mycar.app.features.planes.data.EventoSuscripcionDto> = emptyList(),
    val ultimaOperacion: com.mycar.app.features.planes.data.OperacionPagoDto? = null,
    val vehiculos: Map<String, Any?> = emptyMap()
)

data class AdminVerificarDto(
    val referencia: String? = null,
    val proveedor: String? = null,
    val estado: String? = null,
    val mensaje: String? = null,
    val evidenciaVerificacion: String? = null
)

data class AdminDecisionBody(val motivo: String? = null, val observacion: String? = null)

data class AdminDecisionDto(
    val codigo: String? = null,
    val estado: String? = null,
    val mensaje: String? = null,
    val finUtc: String? = null,
    val modalidad: String? = null
)

interface AdminSuscripcionesApi {
    @GET("api/v1/admin/suscripciones/resumen")
    suspend fun resumen(): Response<AdminSuscripcionesResumenDto>

    @GET("api/v1/admin/suscripciones")
    suspend fun listar(
        @Query("filtro") filtro: String? = null,
        @Query("q") q: String? = null
    ): Response<AdminSuscripcionesPageDto>

    @GET("api/v1/admin/suscripciones/solicitudes")
    suspend fun listarSolicitudes(
        @Query("filtro") filtro: String? = null,
        @Query("q") q: String? = null
    ): Response<AdminSolicitudesPageDto>

    @GET("api/v1/admin/suscripciones/solicitudes/{codigo}")
    suspend fun detalleSolicitud(@Path("codigo") codigo: String): Response<AdminSolicitudDetalleDto>

    @POST("api/v1/admin/suscripciones/solicitudes/{codigo}/aprobar")
    suspend fun aprobar(
        @Path("codigo") codigo: String,
        @Body body: AdminDecisionBody
    ): Response<AdminDecisionDto>

    @POST("api/v1/admin/suscripciones/solicitudes/{codigo}/rechazar")
    suspend fun rechazar(
        @Path("codigo") codigo: String,
        @Body body: AdminDecisionBody
    ): Response<AdminDecisionDto>

    @GET("api/v1/admin/suscripciones/{idUsuario}")
    suspend fun detalle(@Path("idUsuario") idUsuario: Long): Response<AdminSuscripcionDetalleDto>

    @POST("api/v1/admin/suscripciones/operaciones/{referencia}/verificar")
    suspend fun verificar(@Path("referencia") referencia: String): Response<AdminVerificarDto>

    @POST("api/v1/admin/suscripciones/{idUsuario}/cancelar-premium")
    suspend fun cancelarPremium(
        @Path("idUsuario") idUsuario: Long,
        @Body body: AdminDecisionBody
    ): Response<AdminDecisionDto>
}
