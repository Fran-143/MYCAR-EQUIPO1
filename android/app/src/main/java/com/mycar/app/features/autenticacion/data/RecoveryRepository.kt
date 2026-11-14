package com.mycar.app.features.autenticacion.data

import com.google.gson.Gson
import retrofit2.Response

class RecoveryRepository(private val api: RecoveryApi) {
    private val gson = Gson()

    data class Emitido(val mensaje: String, val codigoDesarrollo: String?)
    class RecoveryProblem(message: String, val campos: Map<String, String> = emptyMap()) : Exception(message)

    suspend fun emitir(correo: String): Emitido {
        val response = api.emitir(RecoveryEmailRequest(correo.trim().lowercase()))
        if (response.isSuccessful) {
            val body = response.body()
            return Emitido(body?.mensaje ?: "Enviamos un código temporal a tu correo.", body?.codigoDesarrollo)
        }
        throw problem(response)
    }

    suspend fun verificar(correo: String, codigo: String) {
        val response = api.verificar(RecoveryVerifyRequest(correo.trim().lowercase(), codigo.trim()))
        if (response.isSuccessful) return
        throw problem(response)
    }

    suspend fun restablecer(correo: String, codigo: String, nueva: String, confirmacion: String) {
        val response = api.restablecer(
            RecoveryResetRequest(correo.trim().lowercase(), codigo.trim(), nueva, confirmacion))
        if (response.isSuccessful) return
        throw problem(response)
    }

    private fun problem(response: Response<*>): RecoveryProblem {
        val raw = response.errorBody()?.string().orEmpty()
        val parsed = runCatching { gson.fromJson(raw, RecoveryErrorDto::class.java) }.getOrNull()
        val campos = parsed?.erroresPorCampo.orEmpty()
        val mensaje = when (parsed?.codigo) {
            "CODIGO_EXPIRADO" -> parsed.mensaje ?: "El código expiró. Solicita uno nuevo."
            "CODIGO_INVALIDO" -> parsed.mensaje ?: "El código no es válido."
            "VALIDACION" -> campos.values.firstOrNull() ?: parsed.mensaje ?: "Revisa los datos ingresados."
            else -> parsed?.mensaje ?: "No se pudo completar la recuperación."
        }
        return RecoveryProblem(mensaje, campos)
    }
}
