package com.mycar.app.features.push

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mycar.app.MainActivity
import com.mycar.app.R
import com.mycar.app.features.push.data.PushTokenSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Recibe push FCM y abre MainActivity con extras de destino (DETALLE_ALERTA / bandeja).
 * onNewToken: re-registra al backend si la sesión sigue abierta (segundo dispositivo / rotación).
 */
class MycarFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        MycarPushChannels.asegurarCanalAlertas(this)
    }

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching { PushTokenSync.onTokenRotado(applicationContext, token) }
                .onFailure { Log.i(TAG, "onNewToken sync: ${it.message}") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        MycarPushChannels.asegurarCanalAlertas(this)
        val data = message.data
        val titulo = message.notification?.title ?: data["titulo"] ?: "MyCar"
        val cuerpo = message.notification?.body ?: data["cuerpo"] ?: ""
        val destino = data["destino"] ?: "BANDEJA"
        val idAlerta = data["id_alerta"]?.toLongOrNull()
        val idVehiculo = data["id_vehiculo"]?.toLongOrNull()
        val idNotificacion = data["id_notificacion"]?.toLongOrNull()
        val categoria = data["categoria"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("abrir_desde_push", true)
            putExtra("destino_push", destino)
            if (idAlerta != null) putExtra("id_alerta", idAlerta)
            if (idVehiculo != null) putExtra("id_vehiculo", idVehiculo)
            if (idNotificacion != null) putExtra("id_notificacion", idNotificacion)
            if (!categoria.isNullOrBlank()) putExtra("categoria", categoria)
        }
        val requestCode = (idNotificacion ?: idAlerta ?: (System.currentTimeMillis() and 0x7fffffff)).toInt()
        val pending = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, MycarPushChannels.ALERTAS_ID)
            .setSmallIcon(R.drawable.ic_stat_mycar)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(this)
            .notify((idNotificacion ?: System.currentTimeMillis()).toInt(), notif)
    }

    companion object {
        private const val TAG = "MycarFcm"
    }
}
