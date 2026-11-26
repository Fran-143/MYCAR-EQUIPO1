package com.mycar.app.features.push.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.mycar.app.BuildConfig
import com.mycar.app.features.sesion.data.ClientesSesion
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Registro / baja de token FCM ante el backend autenticado.
 * También alinea RF48 permiso_dispositivo con el permiso OS concedido (sin abrir Preferencias).
 */
object PushTokenSync {
    private const val TAG = "PushTokenSync"
    private const val PREFS = "mycar_push"
    private const val KEY_PENDING_TOKEN = "pending_fcm_token"
    private const val KEY_LAST_REG_MS = "last_fcm_reg_ms"
    private const val MIN_REG_INTERVAL_MS = 90_000L
    const val FCM_TOKEN_TIMEOUT_MS = 15_000L

    suspend fun registrarTrasSesion(
        context: Context,
        accessToken: String,
        installation: String,
        forzar: Boolean = false
    ) {
        if (!forzar && !debeReintentarRegistro(context)) {
            Log.i(TAG, "registro omitido: debounce")
            return
        }
        try {
            // Con forzar: regenerar token (segundo dispositivo / token stale que FCM ya invalidó).
            if (forzar) {
                runCatching { regenerarTokenFcm() }
            }
            // Preferir token vivo de FCM; el pendiente solo cubre rotación antes del login.
            val fcm = obtenerTokenFcm()
                ?: obtenerTokenFcm()
                ?: tokenPendiente(context)
                ?: run {
                    Log.i(TAG, "registro omitido: sin token FCM")
                    return
                }
            val api = ClientesSesion.publico(BuildConfig.API_BASE_URL).create(PushApi::class.java)
            var ok = false
            repeat(2) { intento ->
                if (ok) return@repeat
                val resp = api.registrar(
                    "Bearer $accessToken",
                    RegistrarTokenRequest(installation, "FCM", fcm)
                )
                resp.errorBody()?.close()
                if (resp.isSuccessful) {
                    ok = true
                    limpiarTokenPendiente(context)
                    marcarRegistroOk(context)
                    Log.i(TAG, "registro token OK (intento ${intento + 1})")
                } else {
                    Log.i(TAG, "registro token HTTP ${resp.code()} (intento ${intento + 1})")
                }
            }
            sincronizarPermisoOsSiConcedido(accessToken, context)
        } catch (e: Exception) {
            Log.i(TAG, "registro token omitido: ${e.message}")
        }
    }

    /**
     * Tras conceder POST_NOTIFICATIONS o rotar el token FCM con sesión ya abierta.
     * Sin esto el segundo celular queda sin token activo hasta el próximo login.
     */
    suspend fun registrarSiSesionActiva(context: Context, forzar: Boolean = false) {
        val creds = runCatching {
            com.mycar.app.features.autenticacion.data.AuthGraph.repository(context).peekPushCredentials()
        }.getOrNull() ?: run {
            Log.i(TAG, "re-registro omitido: sin sesión")
            return
        }
        registrarTrasSesion(context, creds.first, creds.second, forzar = forzar)
    }

    /** Guarda rotación FCM y, si hay sesión, la publica al backend de inmediato. */
    suspend fun onTokenRotado(context: Context, token: String) {
        guardarTokenPendiente(context, token)
        registrarSiSesionActiva(context, forzar = true)
    }

    private fun debeReintentarRegistro(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_REG_MS, 0L)
        return System.currentTimeMillis() - last >= MIN_REG_INTERVAL_MS
    }

    private fun marcarRegistroOk(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_REG_MS, System.currentTimeMillis())
            .apply()
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

    /** Persiste rotación FCM hasta el próximo registro autenticado. */
    fun guardarTokenPendiente(context: Context, token: String) {
        if (token.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_TOKEN, token)
            .apply()
        Log.i(TAG, "token pendiente guardado (enmascarado=${enmascarar(token)})")
    }

    private fun tokenPendiente(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_TOKEN, null)
            ?.takeIf { it.isNotBlank() }

    private fun limpiarTokenPendiente(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_TOKEN)
            .apply()
    }

    /**
     * Si el SO ya concedió POST_NOTIFICATIONS, actualiza RF48 a CONCEDIDO.
     * Evita que el outbox omita push por permiso_dispositivo=NO_SOLICITADO tras el diálogo de MainActivity.
     */
    private suspend fun sincronizarPermisoOsSiConcedido(accessToken: String, context: Context) {
        if (!permisoOsConcedido(context)) return
        withContext(Dispatchers.IO) {
            try {
                val base = BuildConfig.API_BASE_URL.trimEnd('/') + "/"
                val body = """{"permisoDispositivo":"CONCEDIDO"}"""
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url(base + "api/v1/preferencias-notificacion/permiso-dispositivo")
                    .patch(body)
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                val client = OkHttpClient.Builder()
                    .callTimeout(30, TimeUnit.SECONDS)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) Log.i(TAG, "RF48 permiso_dispositivo → CONCEDIDO (OS)")
                    else Log.i(TAG, "RF48 permiso sync HTTP ${resp.code}")
                }
            } catch (e: Exception) {
                Log.i(TAG, "RF48 permiso sync omitido: ${e.message}")
            }
        }
    }

    fun permisoOsConcedido(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun enmascarar(token: String): String {
        if (token.length < 12) return "***"
        return token.take(6) + "…" + token.takeLast(4)
    }

    private suspend fun obtenerTokenFcm(): String? =
        withTimeoutOrNull(FCM_TOKEN_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        if (cont.isActive) cont.resume(token?.takeIf { it.isNotBlank() })
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }

    private suspend fun regenerarTokenFcm() {
        withTimeoutOrNull(FCM_TOKEN_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().deleteToken()
                    .addOnCompleteListener {
                        if (cont.isActive) cont.resume(Unit)
                    }
            }
        }
        Log.i(TAG, "token FCM regenerado (deleteToken)")
    }
}
