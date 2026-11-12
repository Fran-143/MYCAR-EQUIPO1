package com.mycar.app.features.push.data

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.mycar.app.BuildConfig
import com.mycar.app.features.sesion.data.ClientesSesion
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Registro / baja de token FCM ante el backend autenticado.
 * No usa Firestore ni Realtime Database.
 */
object PushTokenSync {
    private const val TAG = "PushTokenSync"

    suspend fun registrarTrasSesion(accessToken: String, installation: String) {
        try {
            val fcm = obtenerTokenFcm() ?: return
            val api = ClientesSesion.publico(BuildConfig.API_BASE_URL).create(PushApi::class.java)
            val resp = api.registrar(
                "Bearer $accessToken",
                RegistrarTokenRequest(installation, "FCM", fcm)
            )
            resp.errorBody()?.close()
            if (!resp.isSuccessful) {
                Log.i(TAG, "registro token HTTP ${resp.code()}")
            }
        } catch (e: Exception) {
            Log.i(TAG, "registro token omitido: ${e.message}")
        }
    }

    suspend fun desvincularAlCerrar(accessToken: String?, installation: String) {
        if (accessToken.isNullOrBlank()) return
        try {
            val api = ClientesSesion.publico(BuildConfig.API_BASE_URL).create(PushApi::class.java)
            val resp = api.desvincular(
                "Bearer $accessToken",
                DesvincularTokenRequest(installation)
            )
            resp.errorBody()?.close()
        } catch (_: Exception) {
            // El cierre local ya es efectivo; no bloquea logout.
        }
    }

    private suspend fun obtenerTokenFcm(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (cont.isActive) cont.resume(token?.takeIf { it.isNotBlank() })
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }
}
