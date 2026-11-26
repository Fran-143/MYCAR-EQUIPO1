package com.mycar.app.features.admin.data

import com.mycar.app.BuildConfig
import com.mycar.app.features.autenticacion.data.AuthGraph
import com.mycar.app.features.autenticacion.data.AuthenticatedContext
import com.mycar.app.features.sesion.data.ClienteVehiculosAutenticado
import kotlinx.coroutines.runBlocking

class AdminSuscripcionesRepository(private val api: AdminSuscripcionesApi) {
    suspend fun resumen(): AdminSuscripcionesResumenDto {
        val r = api.resumen()
        if (!r.isSuccessful) throw IllegalStateException(errorMsg(r.errorBody()?.string(), r.code()))
        return r.body() ?: AdminSuscripcionesResumenDto()
    }

    suspend fun listar(filtro: String?, q: String?): List<AdminSuscripcionItemDto> {
        val r = api.listar(filtro, q)
        if (!r.isSuccessful) throw IllegalStateException(errorMsg(r.errorBody()?.string(), r.code()))
        return r.body()?.items.orEmpty()
    }

    suspend fun listarSolicitudes(filtro: String?, q: String?): List<AdminSolicitudItemDto> {
        val r = api.listarSolicitudes(filtro, q)
        if (!r.isSuccessful) throw IllegalStateException(errorMsg(r.errorBody()?.string(), r.code()))
        return r.body()?.items.orEmpty()
    }

    suspend fun detalle(idUsuario: Long): AdminSuscripcionDetalleDto {
        val r = api.detalle(idUsuario)
        if (!r.isSuccessful) throw IllegalStateException(errorMsg(r.errorBody()?.string(), r.code()))
        return r.body() ?: throw IllegalStateException("No se pudo cargar el detalle.")
    }

    suspend fun detalleSolicitud(codigo: String): AdminSolicitudDetalleDto {
        val r = api.detalleSolicitud(codigo)
        if (!r.isSuccessful) throw IllegalStateException(errorMsg(r.errorBody()?.string(), r.code()))
        return r.body() ?: throw IllegalStateException("No se pudo cargar la solicitud.")
    }

    suspend fun aprobar(codigo: String, observacion: String?): AdminDecisionDto {
        val r = api.aprobar(codigo, AdminDecisionBody(observacion = observacion))
        if (!r.isSuccessful) throw IllegalStateException(errorMsg(r.errorBody()?.string(), r.code()))
        return r.body() ?: throw IllegalStateException("Sin respuesta.")
    }

    suspend fun rechazar(codigo: String, motivo: String): AdminDecisionDto {
        val r = api.rechazar(codigo, AdminDecisionBody(motivo = motivo))
        if (!r.isSuccessful) throw IllegalStateException(errorMsg(r.errorBody()?.string(), r.code()))
        return r.body() ?: throw IllegalStateException("Sin respuesta.")
    }

    suspend fun verificar(referencia: String): AdminVerificarDto =
        api.verificar(referencia).body()
            ?: throw IllegalStateException("Sin respuesta de verificación.")

    suspend fun cancelarPremium(idUsuario: Long, motivo: String): AdminDecisionDto {
        val r = api.cancelarPremium(idUsuario, AdminDecisionBody(motivo = motivo))
        if (!r.isSuccessful) throw IllegalStateException(errorMsg(r.errorBody()?.string(), r.code()))
        return r.body() ?: throw IllegalStateException("Sin respuesta.")
    }

    private fun errorMsg(raw: String?, code: Int): String {
        if (raw.isNullOrBlank()) {
            return when (code) {
                401 -> "Sesión caducada. Vuelve a iniciar sesión como administrador."
                403 -> "Sin permiso (403). Confirma MFA o vuelve a iniciar sesión como admin."
                else -> "Error HTTP $code"
            }
        }
        val codigo = Regex("\"codigo\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
        val mensaje = Regex("\"mensaje\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
        if (codigo == "MFA_STEP_UP_REQUERIDO" || raw.contains("MFA", ignoreCase = true)) {
            return mensaje ?: "Confirma MFA reciente (últimos 10 minutos) e inténtalo de nuevo."
        }
        if (codigo == "SESION_NO_VALIDA" || code == 401) {
            return mensaje ?: "Sesión caducada. Vuelve a iniciar sesión como administrador."
        }
        return mensaje ?: raw
    }
}

fun adminSuscripcionesRepository(
    context: android.content.Context,
    auth: AuthenticatedContext
): AdminSuscripcionesRepository {
    val authRepo = AuthGraph.repository(context)
    val cliente = ClienteVehiculosAutenticado(
        BuildConfig.API_BASE_URL,
        // Siempre el contexto vigente + renovación; evita SESION_NO_VALIDA al quedar el snapshot viejo.
        autorizacion = {
            runBlocking {
                val ctx = authRepo.authenticatedContext() ?: auth
                authRepo.authorization(ctx)
            }
        }
    )
    return AdminSuscripcionesRepository(cliente.retrofit.create(AdminSuscripcionesApi::class.java))
}
