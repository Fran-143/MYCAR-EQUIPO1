package com.mycar.app.features.admin.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class AdminUsuariosPageDto(
    val page: Int = 0,
    val size: Int = 20,
    val total: Int = 0,
    val items: List<AdminUsuarioResumenDto> = emptyList()
)

data class AdminUsuarioResumenDto(
    val idUsuario: Long = 0,
    val nombre: String? = null,
    val correo: String? = null,
    val estado: String? = null,
    val estadoVisible: String? = null,
    val rol: String? = null,
    val tipoBloqueo: String? = null,
    val desbloqueoUtc: String? = null,
    val anonima: Boolean = false,
    val constancia: String? = null
)

data class AdminUsuarioDetalleDto(
    val idUsuario: Long = 0,
    val nombreCompleto: String? = null,
    val nombre: String? = null,
    val apellido: String? = null,
    val correo: String? = null,
    val rol: String? = null,
    val estado: String? = null,
    val estadoVisible: String? = null,
    val tipoBloqueo: String? = null,
    val tipoBloqueoVisible: String? = null,
    val fechaRegistroUtc: String? = null,
    val ultimoAccesoUtc: String? = null,
    val motivoEstado: String? = null,
    val observacionEstado: String? = null,
    val bloqueoAdminHastaUtc: String? = null,
    val loginBloqueoHastaUtc: String? = null,
    val eliminacionProgramadaUtc: String? = null,
    val constancia: String? = null,
    val anonima: Boolean = false,
    val restriccion: String? = null,
    val vehiculos: Map<String, Any?>? = null,
    val suscripcion: Map<String, Any?>? = null,
    val solicitudPremiumAbierta: Map<String, Any?>? = null,
    val solicitudEliminacion: Map<String, Any?>? = null,
    val restriccionRecuperacion: Map<String, Any?>? = null
)

data class AdminHistorialItemDto(
    val idEvento: Long = 0,
    val accion: String? = null,
    val estadoAnterior: String? = null,
    val estadoPosterior: String? = null,
    val motivo: String? = null,
    val resultado: String? = null,
    val creadoUtc: String? = null
)

data class AdminMotivoBody(val motivo: String, val comentario: String? = null)
data class AdminBloquearBody(val motivo: String, val duracionMinutos: Int, val observacion: String? = null)
data class AdminReactivarAsistidaBody(val motivo: String, val observacion: String? = null)
data class AdminVerificarCodigoAsistidaBody(val codigo: String)

interface AdminUsuariosApi {
    @GET("api/v1/admin/usuarios")
    suspend fun listar(
        @Query("q") q: String? = null,
        @Query("estado") estado: String? = null,
        @Query("rol") rol: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<AdminUsuariosPageDto>

    @GET("api/v1/admin/usuarios/{id}")
    suspend fun detalle(@Path("id") id: Long): Response<AdminUsuarioDetalleDto>

    @GET("api/v1/admin/usuarios/{id}/historial")
    suspend fun historial(@Path("id") id: Long): Response<List<AdminHistorialItemDto>>

    @POST("api/v1/admin/usuarios/{id}/suspender")
    suspend fun suspender(@Path("id") id: Long, @Body body: AdminMotivoBody): Response<Map<String, Any>>

    @POST("api/v1/admin/usuarios/{id}/reactivar")
    suspend fun reactivar(@Path("id") id: Long, @Body body: AdminMotivoBody): Response<Map<String, Any>>

    @POST("api/v1/admin/usuarios/{id}/bloquear")
    suspend fun bloquear(@Path("id") id: Long, @Body body: AdminBloquearBody): Response<Map<String, Any>>

    @POST("api/v1/admin/usuarios/{id}/desbloquear")
    suspend fun desbloquear(@Path("id") id: Long, @Body body: AdminMotivoBody): Response<Map<String, Any>>

    @GET("api/v1/admin/usuarios/{id}/asistencia-recuperacion")
    suspend fun asistencia(@Path("id") id: Long): Response<Map<String, Any>>

    @GET("api/v1/admin/usuarios/{id}/reactivacion-asistida")
    suspend fun estadoReactivacionAsistida(@Path("id") id: Long): Response<Map<String, Any>>

    @POST("api/v1/admin/usuarios/{id}/reactivacion-asistida/iniciar-verificacion")
    suspend fun iniciarVerificacionAsistida(@Path("id") id: Long): Response<Map<String, Any>>

    @POST("api/v1/admin/usuarios/{id}/reactivacion-asistida/verificar-codigo")
    suspend fun verificarCodigoAsistida(
        @Path("id") id: Long,
        @Body body: AdminVerificarCodigoAsistidaBody
    ): Response<Map<String, Any>>

    @POST("api/v1/admin/usuarios/{id}/reactivacion-asistida/confirmar")
    suspend fun confirmarReactivacionAsistida(
        @Path("id") id: Long,
        @Body body: AdminReactivarAsistidaBody
    ): Response<Map<String, Any>>
}
