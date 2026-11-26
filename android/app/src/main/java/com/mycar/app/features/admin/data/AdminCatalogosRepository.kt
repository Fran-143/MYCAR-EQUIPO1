package com.mycar.app.features.admin.data

import com.mycar.app.BuildConfig
import com.mycar.app.features.autenticacion.data.AuthGraph
import com.mycar.app.features.autenticacion.data.AuthenticatedContext
import com.mycar.app.features.sesion.data.ClienteVehiculosAutenticado
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class AdminCatalogosRepository(private val api: AdminCatalogosApi) {

    suspend fun listarTipos(q: String?, estado: String): CatalogoListaDto =
        unwrap(api.listarTipos(q?.ifBlank { null }, estado))

    suspend fun crearTipo(nombre: String): CatalogoItemDto =
        unwrap(api.crearTipo(CatalogoNombreBody(nombre)))

    suspend fun editarTipo(id: Long, nombre: String): CatalogoItemDto =
        unwrap(api.editarTipo(id, CatalogoNombreBody(nombre)))

    suspend fun desactivarTipo(id: Long): CatalogoItemDto = unwrap(api.desactivarTipo(id))
    suspend fun reactivarTipo(id: Long): CatalogoItemDto = unwrap(api.reactivarTipo(id))

    suspend fun listarAseguradoras(q: String?, estado: String): CatalogoListaDto =
        unwrap(api.listarAseguradoras(q?.ifBlank { null }, estado))

    suspend fun crearAseguradora(nombre: String): CatalogoItemDto =
        unwrap(api.crearAseguradora(CatalogoNombreBody(nombre)))

    suspend fun editarAseguradora(id: Long, nombre: String): CatalogoItemDto =
        unwrap(api.editarAseguradora(id, CatalogoNombreBody(nombre)))

    suspend fun desactivarAseguradora(id: Long): CatalogoItemDto = unwrap(api.desactivarAseguradora(id))
    suspend fun reactivarAseguradora(id: Long): CatalogoItemDto = unwrap(api.reactivarAseguradora(id))

    suspend fun listarTalleres(q: String?, estado: String): CatalogoListaDto =
        unwrap(api.listarTalleres(q?.ifBlank { null }, estado))

    suspend fun detalleTaller(id: Long): CatalogoItemDto = unwrap(api.detalleTaller(id))

    suspend fun crearTaller(body: TallerGlobalBody): CatalogoItemDto = unwrap(api.crearTaller(body))
    suspend fun editarTaller(id: Long, body: TallerGlobalBody): CatalogoItemDto =
        unwrap(api.editarTaller(id, body))

    suspend fun desactivarTaller(id: Long): CatalogoItemDto = unwrap(api.desactivarTaller(id))
    suspend fun reactivarTaller(id: Long): CatalogoItemDto = unwrap(api.reactivarTaller(id))

    private fun <T> unwrap(r: retrofit2.Response<T>): T {
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

fun adminCatalogosRepository(context: android.content.Context, auth: AuthenticatedContext): AdminCatalogosRepository {
    val authRepo = AuthGraph.repository(context)
    val cliente = ClienteVehiculosAutenticado(
        BuildConfig.API_BASE_URL,
        autorizacion = { runBlocking { authRepo.authorization(auth) } }
    )
    return AdminCatalogosRepository(cliente.retrofit.create(AdminCatalogosApi::class.java))
}
