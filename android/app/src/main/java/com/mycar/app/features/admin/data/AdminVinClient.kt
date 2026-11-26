package com.mycar.app.features.admin.data

import com.mycar.app.BuildConfig
import com.mycar.app.features.autenticacion.data.AuthGraph
import com.mycar.app.features.autenticacion.data.AuthenticatedContext
import com.mycar.app.features.sesion.data.ClienteVehiculosAutenticado
import kotlinx.coroutines.runBlocking

fun adminVinRepository(context: android.content.Context, auth: AuthenticatedContext): RemoteAdminVinRepository {
    val authRepo = AuthGraph.repository(context)
    val cliente = ClienteVehiculosAutenticado(
        BuildConfig.API_BASE_URL,
        autorizacion = { runBlocking { authRepo.authorization(auth) } }
    )
    return RemoteAdminVinRepository(cliente.retrofit.create(AdminVinApi::class.java))
}