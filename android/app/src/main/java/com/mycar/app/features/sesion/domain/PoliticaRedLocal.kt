package com.mycar.app.features.sesion.domain

import java.net.URI
import java.util.Locale

/** El permiso sólo corresponde al servidor local de esta variante DEBUG, desde Android 17. */
object PoliticaRedLocal {
    const val PERMISO = "android.permission.ACCESS_LOCAL_NETWORK"
    fun requierePermiso(debug: Boolean, sdk: Int, baseUrl: String): Boolean {
        if (!debug || sdk < 37) return false
        val host = try { URI(baseUrl).host?.lowercase(Locale.ROOT) } catch (_: Exception) { null }
        return host in setOf("10.0.2.2", "127.0.0.1")
    }
}
