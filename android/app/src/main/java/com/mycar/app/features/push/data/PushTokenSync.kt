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
    private const val MIN_REG_INTERVAL_MS = 45_000L
    /** Tras invalidación FCM en servidor, forzar re-registro periódico en resume. */
    private const val FORCE_REG_INTERVAL_MS = 3 * 60 * 60_000L
    const val FCM_TOKEN_TIMEOUT_MS = 15_000L

    suspend fun registrarTrasSesion(
        context: Context,
        accessToken: String,
        installation: String,
        forzar: Boolean = false
    ) {
        val forzarPorAntiguedad = !forzar && debeForzarPorAntiguedad(context)
        val forzarEfectivo = forzar || forzarPorAntiguedad
        if (!forzarEfectivo && !debeReintentarRegistro(context)) {
            Log.i(TAG, "registro omitido: debounce")
            return
        }
        try {
            // NUNCA deleteToken aquí: deleteToken → onNewToken → forzar → deleteToken = bucle
            // que deja el celular sin token activo en el backend (push nunca llega).
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

    /**
     * Rotación FCM: publicar el token nuevo. No regenerar (evitar bucle deleteToken).
     */
    suspend fun onTokenRotado(context: Context, token: String) {
        guardarTokenPendiente(context, token)
        registrarSiSesionActiva(context, forzar = true)
    }

    private fun debeReintentarRegistro(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_REG_MS, 0L)
        return System.currentTimeMillis() - last >= MIN_REG_INTERVAL_MS
    }

    private fun debeForzarPorAntiguedad(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_REG_MS, 0L)
        return last == 0L || System.currentTimeMillis() - last >= FORCE_REG_INTERVAL_MS
    }

    /**
     * Prueba FCM genérica — envía token vivo en el body para upsert+FCM atómico.
     * Siempre muestra notificación local si el canal/permiso lo permiten.
     */
    suspend fun probarPush(context: Context): String {
        val creds = com.mycar.app.features.autenticacion.data.AuthGraph.repository(context)
            .peekPushCredentials()
            ?: return "Sin sesión activa."
        val fcm = obtenerTokenFcm() ?: tokenPendiente(context)
        return withContext(Dispatchers.IO) {
            try {
                val api = ClientesSesion.publico(BuildConfig.API_BASE_URL).create(PushApi::class.java)
                val resp = api.probar(
                    "Bearer ${creds.first}",
                    ProbarPushBody(idInstalacion = creds.second, token = fcm)
                )
                val body = resp.body()
                when {
                    resp.isSuccessful && body != null -> {
                        // Local siempre: evidencia inmediata en sombra aunque FCM tarde/falle.
                        mostrarNotificacionLocalPrueba(
                            context,
                            "MyCar — prueba FCM",
                            "Notificación de verificación en tu dispositivo."
                        )
                        when {
                            body.ok -> "Push FCM OK (${body.enviados}/${body.tokens}). Mira la sombra del sistema."
                            body.tokens == 0 -> "Sin token en servidor. Revisa permiso de notificaciones y red."
                            else -> "FCM no entregó (env=${body.enviados}, inv=${body.invalidos}, err=${body.errores}). Notif local mostrada."
                        }
                    }
                    resp.code() == 401 -> "Sesión no válida."
                    resp.code() == 503 -> "FCM no configurado en el servidor."
                    else -> "Error HTTP ${resp.code()} al probar push."
                }
            } catch (e: Exception) {
                e.message ?: "No se pudo probar el push."
            }
        }
    }

    /**
     * Prueba recordatorio KM: bandeja + FCM. Manda token vivo; notif local siempre si bandeja OK.
     */
    suspend fun probarPushKm(context: Context): String {
        val creds = com.mycar.app.features.autenticacion.data.AuthGraph.repository(context)
            .peekPushCredentials()
            ?: return "Sin sesión activa."
        val fcm = obtenerTokenFcm() ?: tokenPendiente(context)
        return withContext(Dispatchers.IO) {
            try {
                val api = ClientesSesion.publico(BuildConfig.API_BASE_URL).create(PushApi::class.java)
                val resp = api.probarKm(
                    "Bearer ${creds.first}",
                    ProbarPushBody(idInstalacion = creds.second, token = fcm)
                )
                val body = resp.body()
                when {
                    resp.isSuccessful && body != null -> {
                        if (body.bandejaCreada || body.enviados > 0) {
                            mostrarNotificacionLocalPrueba(
                                context,
                                "Prueba — actualiza el kilometraje",
                                "Aviso de prueba de recordatorio de kilometraje. Revisa tu bandeja en MyCar."
                            )
                        }
                        val bandeja = if (body.bandejaCreada) "Bandeja OK" else "Bandeja no creada"
                        val push = when {
                            body.enviados > 0 -> "Push FCM OK (${body.enviados}/${body.tokens})"
                            body.tokens == 0 -> "Sin token FCM en servidor"
                            else -> "Push FCM falló (inv=${body.invalidos}, err=${body.errores})"
                        }
                        "$bandeja · $push · notif=${body.idNotificacion ?: "-"}"
                    }
                    resp.code() == 400 -> "Necesitas un vehículo activo para la prueba KM."
                    resp.code() == 401 -> "Sesión no válida."
                    resp.code() == 503 -> "FCM no configurado en el servidor."
                    else -> "Error HTTP ${resp.code()} al probar KM."
                }
            } catch (e: Exception) {
                e.message ?: "No se pudo probar el recordatorio KM."
            }
        }
    }

    private fun mostrarNotificacionLocalPrueba(context: Context, titulo: String, cuerpo: String) {
        try {
            com.mycar.app.features.push.MycarPushChannels.asegurarCanalAlertas(context)
            val nm = androidx.core.app.NotificationManagerCompat.from(context)
            if (!nm.areNotificationsEnabled()) {
                Log.i(TAG, "notif local omitida: permiso denegado")
                return
            }
            val intent = android.content.Intent(context, com.mycar.app.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("abrir_desde_push", true)
            }
            val pending = android.app.PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() and 0x7fffffff).toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notif = androidx.core.app.NotificationCompat.Builder(
                context,
                com.mycar.app.features.push.MycarPushChannels.ALERTAS_ID
            )
                .setSmallIcon(com.mycar.app.R.drawable.ic_stat_mycar)
                .setContentTitle(titulo)
                .setContentText(cuerpo)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                .build()
            nm.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notif)
            Log.i(TAG, "notif local de prueba publicada")
        } catch (e: Exception) {
            Log.i(TAG, "notif local omitida: ${e.message}")
        }
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
}
