package com.mycar.app.features.preferenciasnotif.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.PUT

data class PreferenciasDto(
    val mantenimiento: Boolean? = null,
    val soat: Boolean? = null,
    val kilometraje: Boolean? = null,
    val permisoDispositivo: String? = null
)

data class PreferenciasErrorDto(
    val codigo: String? = null,
    val mensaje: String? = null
)

data class PermisoDispositivoRequest(val permisoDispositivo: String)

interface PreferenciasNotificacionApi {
    @GET("api/v1/preferencias-notificacion")
    suspend fun consultar(): Response<PreferenciasDto>

    @PUT("api/v1/preferencias-notificacion")
    suspend fun guardar(@Body body: PreferenciasDto): Response<PreferenciasDto>

    @PATCH("api/v1/preferencias-notificacion/permiso-dispositivo")
    suspend fun actualizarPermiso(@Body body: PermisoDispositivoRequest): Response<PreferenciasDto>
}
