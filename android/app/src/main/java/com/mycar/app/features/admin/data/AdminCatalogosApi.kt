package com.mycar.app.features.admin.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class CatalogoNombreBody(val nombre: String)

data class CatalogoListaDto(
    val items: List<CatalogoItemDto> = emptyList(),
    val total: Int = 0
)

data class CatalogoItemDto(
    val id: Long = 0,
    val codigo: String? = null,
    val nombre: String? = null,
    val activo: Boolean? = null,
    val estado: String? = null,
    val telefono: String? = null,
    val correo: String? = null,
    val direccion: String? = null,
    val tieneWhatsapp: Boolean? = null,
    val contactoAutorizado: Boolean? = null,
    val notasPublicas: String? = null,
    val permiteNombreLibre: Boolean? = null,
    val esDemo: Boolean? = null,
    val creadoUtc: String? = null,
    val actualizadoUtc: String? = null
)

data class TallerGlobalBody(
    val nombre: String,
    val telefono: String? = null,
    val correo: String? = null,
    val direccion: String? = null,
    val tieneWhatsapp: Boolean = false,
    val contactoAutorizado: Boolean = false,
    val notasPublicas: String? = null
)

interface AdminCatalogosApi {
    @GET("api/v1/admin/catalogos/tipos-mantenimiento")
    suspend fun listarTipos(
        @Query("q") q: String? = null,
        @Query("estado") estado: String? = "TODOS"
    ): Response<CatalogoListaDto>

    @POST("api/v1/admin/catalogos/tipos-mantenimiento")
    suspend fun crearTipo(@Body body: CatalogoNombreBody): Response<CatalogoItemDto>

    @PUT("api/v1/admin/catalogos/tipos-mantenimiento/{id}")
    suspend fun editarTipo(@Path("id") id: Long, @Body body: CatalogoNombreBody): Response<CatalogoItemDto>

    @POST("api/v1/admin/catalogos/tipos-mantenimiento/{id}/desactivar")
    suspend fun desactivarTipo(@Path("id") id: Long): Response<CatalogoItemDto>

    @POST("api/v1/admin/catalogos/tipos-mantenimiento/{id}/reactivar")
    suspend fun reactivarTipo(@Path("id") id: Long): Response<CatalogoItemDto>

    @GET("api/v1/admin/catalogos/aseguradoras-soat")
    suspend fun listarAseguradoras(
        @Query("q") q: String? = null,
        @Query("estado") estado: String? = "TODOS"
    ): Response<CatalogoListaDto>

    @POST("api/v1/admin/catalogos/aseguradoras-soat")
    suspend fun crearAseguradora(@Body body: CatalogoNombreBody): Response<CatalogoItemDto>

    @PUT("api/v1/admin/catalogos/aseguradoras-soat/{id}")
    suspend fun editarAseguradora(@Path("id") id: Long, @Body body: CatalogoNombreBody): Response<CatalogoItemDto>

    @POST("api/v1/admin/catalogos/aseguradoras-soat/{id}/desactivar")
    suspend fun desactivarAseguradora(@Path("id") id: Long): Response<CatalogoItemDto>

    @POST("api/v1/admin/catalogos/aseguradoras-soat/{id}/reactivar")
    suspend fun reactivarAseguradora(@Path("id") id: Long): Response<CatalogoItemDto>

    @GET("api/v1/admin/catalogos/talleres-globales")
    suspend fun listarTalleres(
        @Query("q") q: String? = null,
        @Query("estado") estado: String? = "TODOS"
    ): Response<CatalogoListaDto>

    @GET("api/v1/admin/catalogos/talleres-globales/{id}")
    suspend fun detalleTaller(@Path("id") id: Long): Response<CatalogoItemDto>

    @POST("api/v1/admin/catalogos/talleres-globales")
    suspend fun crearTaller(@Body body: TallerGlobalBody): Response<CatalogoItemDto>

    @PUT("api/v1/admin/catalogos/talleres-globales/{id}")
    suspend fun editarTaller(@Path("id") id: Long, @Body body: TallerGlobalBody): Response<CatalogoItemDto>

    @POST("api/v1/admin/catalogos/talleres-globales/{id}/desactivar")
    suspend fun desactivarTaller(@Path("id") id: Long): Response<CatalogoItemDto>

    @POST("api/v1/admin/catalogos/talleres-globales/{id}/reactivar")
    suspend fun reactivarTaller(@Path("id") id: Long): Response<CatalogoItemDto>
}
