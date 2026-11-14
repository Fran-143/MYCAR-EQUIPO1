package com.mycar.app.features.perfil.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

data class PerfilDto(
    val nombre: String? = null,
    val apellido: String? = null,
    val correo: String? = null,
    val fechaNacimiento: String? = null,
    val nombreCompleto: String? = null
)

data class ActualizarPerfilRequest(
    val nombre: String,
    val apellido: String,
    val fechaNacimiento: String
)

data class CambiarContrasenaRequest(
    val contrasenaActual: String,
    val nuevaContrasena: String,
    val confirmacionContrasena: String
)

data class VerificarContrasenaRequest(val contrasenaActual: String)

data class CambiarContrasenaResponse(val resultado: String? = null)

data class VerificarContrasenaResponse(val resultado: String? = null)

data class PerfilErrorDto(
    val codigo: String? = null,
    val mensaje: String? = null,
    val erroresPorCampo: Map<String, String>? = null
)

interface PerfilApi {
    @GET("api/v1/perfil")
    suspend fun consultar(): Response<PerfilDto>

    @PUT("api/v1/perfil")
    suspend fun actualizar(@Body body: ActualizarPerfilRequest): Response<PerfilDto>

    @POST("api/v1/perfil/contrasena/verificar")
    suspend fun verificarContrasena(@Body body: VerificarContrasenaRequest): Response<VerificarContrasenaResponse>

    @POST("api/v1/perfil/contrasena")
    suspend fun cambiarContrasena(@Body body: CambiarContrasenaRequest): Response<CambiarContrasenaResponse>
}
