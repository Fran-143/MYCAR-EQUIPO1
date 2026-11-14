package com.mycar.app.features.talleres.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface TalleresApi {
    @GET("api/v1/rf34/talleres")
    suspend fun listar(@Query("origen") origen: String = "TODOS"): ListaTalleresDto

    @GET("api/v1/rf34/talleres/catalogo-global")
    suspend fun catalogoGlobal(@Query("q") q: String? = null): ListaTalleresGlobalesDto

    @GET("api/v1/rf34/talleres/{id}")
    suspend fun detalle(@Path("id") id: Long): TallerDto

    @POST("api/v1/rf34/talleres")
    suspend fun registrar(@Body body: RegistrarTallerDto): TallerDto

    @POST("api/v1/rf34/talleres/asociados")
    suspend fun vincularAsociado(@Body body: VincularAsociadoDto): TallerDto

    @PUT("api/v1/rf34/talleres/{id}")
    suspend fun actualizar(@Path("id") id: Long, @Body body: ActualizarTallerDto): TallerDto

    @POST("api/v1/rf34/talleres/{id}/retirar")
    suspend fun retirar(@Path("id") id: Long, @Body body: RetirarTallerDto): TallerDto

    @POST("api/v1/rf34/talleres/{id}/favorito")
    suspend fun marcarFavorito(@Path("id") id: Long): TallerDto

    @DELETE("api/v1/rf34/talleres/{id}/favorito")
    suspend fun desmarcarFavorito(@Path("id") id: Long): TallerDto
}

data class ListaTalleresDto(val talleres: List<TallerDto?>?)
data class ListaTalleresGlobalesDto(val talleres: List<TallerGlobalDto?>?)
data class TallerDto(
    val idTaller: Long?,
    val nombre: String?,
    val telefono: String?,
    val correo: String?,
    val direccion: String?,
    val notas: String?,
    val estado: String?,
    val favorito: Boolean?,
    val versionFila: Int?,
    val creadoUtc: String? = null,
    val origen: String? = null,
    val tieneWhatsapp: Boolean? = null,
    val idTallerGlobal: Long? = null,
    val editable: Boolean? = null
)
data class TallerGlobalDto(
    val idTallerGlobal: Long?,
    val nombre: String?,
    val telefono: String?,
    val correo: String?,
    val direccion: String?,
    val tieneWhatsapp: Boolean?,
    val estado: String?
)
data class RegistrarTallerDto(
    val nombre: String,
    val telefono: String? = null,
    val correo: String? = null,
    val direccion: String? = null,
    val notas: String? = null,
    val tieneWhatsapp: Boolean = false
)
data class VincularAsociadoDto(val idTallerGlobal: Long)
data class ActualizarTallerDto(
    val nombre: String,
    val telefono: String? = null,
    val correo: String? = null,
    val direccion: String? = null,
    val notas: String? = null,
    val tieneWhatsapp: Boolean? = null,
    val versionFila: Int
)
data class RetirarTallerDto(val versionFila: Int)
data class ErrorTallerDto(val codigo: String?, val mensaje: String?)
