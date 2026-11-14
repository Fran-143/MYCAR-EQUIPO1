package com.mycar.app.features.perfil.data

import com.google.gson.Gson
import com.mycar.app.features.perfil.domain.DatosPerfil
import com.mycar.app.features.perfil.domain.ErrorPerfil
import com.mycar.app.features.perfil.domain.PerfilRepository
import retrofit2.Response

class RemotePerfilRepository(private val api: PerfilApi) : PerfilRepository {
    private val gson = Gson()

    override suspend fun consultar(): DatosPerfil = map(api.consultar())

    override suspend fun actualizar(nombre: String, apellido: String, fechaNacimientoIso: String): DatosPerfil =
        map(api.actualizar(ActualizarPerfilRequest(nombre, apellido, fechaNacimientoIso)))

    override suspend fun verificarContrasenaActual(actual: String) {
        val response = api.verificarContrasena(VerificarContrasenaRequest(actual))
        if (response.isSuccessful) return
        throw errorOf(response)
    }

    override suspend fun cambiarContrasena(actual: String, nueva: String, confirmacion: String) {
        val response = api.cambiarContrasena(CambiarContrasenaRequest(actual, nueva, confirmacion))
        if (response.isSuccessful) return
        throw errorOf(response)
    }

    private fun map(response: Response<PerfilDto>): DatosPerfil {
        if (response.isSuccessful) {
            val body = response.body() ?: throw ErrorPerfil.Red("Respuesta vacía del servidor.")
            return DatosPerfil(
                nombre = body.nombre.orEmpty().trim(),
                apellido = body.apellido.orEmpty().trim(),
                correo = body.correo.orEmpty().trim(),
                fechaNacimientoIso = body.fechaNacimiento.orEmpty().trim()
            )
        }
        throw errorOf(response)
    }

    private fun errorOf(response: Response<*>): ErrorPerfil {
        if (response.code() == 401) return ErrorPerfil.Sesion()
        val raw = response.errorBody()?.string().orEmpty()
        val parsed = runCatching { gson.fromJson(raw, PerfilErrorDto::class.java) }.getOrNull()
        val campos = parsed?.erroresPorCampo.orEmpty()
        if (response.code() == 400 || parsed?.codigo == "VALIDACION") {
            return ErrorPerfil.Validacion(
                campos.ifEmpty { mapOf("contrasenaActual" to (parsed?.mensaje ?: "Revisa los datos.")) },
                campos.values.firstOrNull() ?: parsed?.mensaje ?: "Revisa los datos del perfil."
            )
        }
        return ErrorPerfil.Red(parsed?.mensaje ?: "No se pudo completar la operación.")
    }
}
