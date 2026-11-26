package com.mycar.app.features.admin.data

import com.mycar.app.BuildConfig
import com.mycar.app.features.autenticacion.data.AuthGraph
import com.mycar.app.features.autenticacion.data.AuthenticatedContext
import com.mycar.app.features.sesion.data.ClienteVehiculosAutenticado
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class AdminUsuariosRepository(private val api: AdminUsuariosApi) {
    suspend fun listar(q: String?, estado: String?, rol: String?, page: Int): AdminUsuariosPageDto {
        val r = api.listar(q?.ifBlank { null }, estado?.ifBlank { null }, rol?.ifBlank { null }, page, 20)
        if (!r.isSuccessful || r.body() == null) throw error(r.code(), r.errorBody()?.string())
        return r.body()!!
    }

    suspend fun detalle(id: Long): AdminUsuarioDetalleDto {
        val r = api.detalle(id)
        if (!r.isSuccessful || r.body() == null) throw error(r.code(), r.errorBody()?.string())
        return r.body()!!
    }

    suspend fun historial(id: Long): List<AdminHistorialItemDto> {
        val r = api.historial(id)
        if (!r.isSuccessful || r.body() == null) throw error(r.code(), r.errorBody()?.string())
        return r.body()!!
    }

    suspend fun suspender(id: Long, motivo: String, comentario: String?) {
        val r = api.suspender(id, AdminMotivoBody(motivo, comentario))
        if (!r.isSuccessful) throw error(r.code(), r.errorBody()?.string())
    }

    suspend fun reactivar(id: Long, motivo: String) {
        val r = api.reactivar(id, AdminMotivoBody(motivo))
        if (!r.isSuccessful) throw error(r.code(), r.errorBody()?.string())
    }

    suspend fun bloquear(id: Long, motivo: String, minutos: Int, observacion: String?) {
        val r = api.bloquear(id, AdminBloquearBody(motivo, minutos, observacion))
        if (!r.isSuccessful) throw error(r.code(), r.errorBody()?.string())
    }

    suspend fun desbloquear(id: Long, motivo: String) {
        val r = api.desbloquear(id, AdminMotivoBody(motivo))
        if (!r.isSuccessful) throw error(r.code(), r.errorBody()?.string())
    }

    suspend fun asistencia(id: Long): Map<String, Any> {
        val r = api.asistencia(id)
        if (!r.isSuccessful || r.body() == null) throw error(r.code(), r.errorBody()?.string())
        return r.body()!!
    }

    suspend fun estadoReactivacionAsistida(id: Long): Map<String, Any> {
        val r = api.estadoReactivacionAsistida(id)
        if (!r.isSuccessful || r.body() == null) throw error(r.code(), r.errorBody()?.string())
        return r.body()!!
    }

    suspend fun iniciarVerificacionAsistida(id: Long): Map<String, Any> {
        val r = api.iniciarVerificacionAsistida(id)
        if (!r.isSuccessful || r.body() == null) throw error(r.code(), r.errorBody()?.string())
        return r.body()!!
    }

    suspend fun verificarCodigoAsistida(id: Long, codigo: String): Map<String, Any> {
        val r = api.verificarCodigoAsistida(id, AdminVerificarCodigoAsistidaBody(codigo))
        if (!r.isSuccessful || r.body() == null) throw error(r.code(), r.errorBody()?.string())
        return r.body()!!
    }

    suspend fun confirmarReactivacionAsistida(id: Long, motivo: String, observacion: String?): Map<String, Any> {
        val r = api.confirmarReactivacionAsistida(id, AdminReactivarAsistidaBody(motivo, observacion))
        if (!r.isSuccessful || r.body() == null) throw error(r.code(), r.errorBody()?.string())
        return r.body()!!
    }

    private fun error(code: Int, raw: String?): IllegalStateException {
        val msg = try {
            JSONObject(raw ?: "{}").optString("mensaje").ifBlank { "Error $code" }
        } catch (_: Exception) {
            raw?.take(200) ?: "Error $code"
        }
        return IllegalStateException(msg)
    }
}

fun adminUsuariosRepository(context: android.content.Context, auth: AuthenticatedContext): AdminUsuariosRepository {
    val authRepo = AuthGraph.repository(context)
    val cliente = ClienteVehiculosAutenticado(
        BuildConfig.API_BASE_URL,
        autorizacion = { runBlocking { authRepo.authorization(auth) } }
    )
    return AdminUsuariosRepository(cliente.retrofit.create(AdminUsuariosApi::class.java))
}
