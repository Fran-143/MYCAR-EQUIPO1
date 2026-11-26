package com.mycar.app.features.preferenciasnotif.data

import com.google.gson.Gson
import com.mycar.app.features.preferenciasnotif.domain.ErrorPreferencias
import com.mycar.app.features.preferenciasnotif.domain.PermisoDispositivo
import com.mycar.app.features.preferenciasnotif.domain.PreferenciasExternas
import com.mycar.app.features.preferenciasnotif.domain.PreferenciasNotificacionRepository
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class RemotePreferenciasNotificacionRepository(
    private val api: PreferenciasNotificacionApi
) : PreferenciasNotificacionRepository {
    private val gson = Gson()

    override suspend fun consultar(): PreferenciasExternas = map(api.consultar())

    override suspend fun guardar(
        mantenimiento: Boolean,
        soat: Boolean,
        kilometraje: Boolean,
        permisoDispositivo: PermisoDispositivo
    ): PreferenciasExternas = map(
        api.guardar(
            PreferenciasDto(
                mantenimiento = mantenimiento,
                soat = soat,
                kilometraje = kilometraje,
                permisoDispositivo = permisoDispositivo.api
            )
        )
    )

    override suspend fun actualizarPermisoDispositivo(permiso: PermisoDispositivo): PreferenciasExternas =
        map(api.actualizarPermiso(PermisoDispositivoRequest(permiso.api)))

    private fun map(response: Response<PreferenciasDto>): PreferenciasExternas {
        if (response.code() == 401) throw ErrorPreferencias.Sesion()
        if (!response.isSuccessful) throw errorOf(response)
        val body = response.body() ?: throw ErrorPreferencias.Red("Respuesta vacía del servidor.")
        return PreferenciasExternas(
            mantenimiento = body.mantenimiento ?: true,
            soat = body.soat ?: true,
            kilometraje = body.kilometraje ?: true,
            permisoDispositivo = PermisoDispositivo.fromApi(body.permisoDispositivo)
        )
    }

    private fun errorOf(response: Response<*>): ErrorPreferencias {
        val raw = response.errorBody()?.string().orEmpty()
        val parsed = runCatching { gson.fromJson(raw, PreferenciasErrorDto::class.java) }.getOrNull()
        val mensaje = parsed?.mensaje ?: "No pudimos guardar tus preferencias"
        return ErrorPreferencias.Red(mensaje)
    }

    companion object {
        fun mensajeHttp(e: Throwable, fallback: String): String = when (e) {
            is ErrorPreferencias -> e.message ?: fallback
            is HttpException -> fallback
            is IOException -> e.message ?: fallback
            else -> fallback
        }
    }
}
