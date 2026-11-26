package com.mycar.app.features.admin.data

import com.mycar.app.BuildConfig
import com.mycar.app.features.autenticacion.data.AuthGraph
import com.mycar.app.features.autenticacion.data.AuthenticatedContext
import com.mycar.app.features.sesion.data.ClienteVehiculosAutenticado
import kotlinx.coroutines.runBlocking

fun adminGeneralesRepository(context: android.content.Context, auth: AuthenticatedContext): RemoteAdminGeneralesRepository {
    val authRepo = AuthGraph.repository(context)
    val cliente = ClienteVehiculosAutenticado(
        BuildConfig.API_BASE_URL,
        autorizacion = { runBlocking { authRepo.authorization(auth) } }
    )
    return RemoteAdminGeneralesRepository(cliente.retrofit.create(AdminGeneralesApi::class.java))
}