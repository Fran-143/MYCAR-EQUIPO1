package com.mycar.app.features.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mycar.app.MainActivity
import com.mycar.app.R

/**
 * Recibe push FCM y abre MainActivity con extras de destino (DETALLE_ALERTA / bandeja).
 * onNewToken: el re-registro ocurre en el próximo login/validateSession.
 */
class MycarFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Persistencia del token en backend requiere JWT; se sincroniza al autenticar.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val titulo = message.notification?.title ?: data["titulo"] ?: "MyCar"
        val cuerpo = message.notification?.body ?: data["cuerpo"] ?: ""
        val destino = data["destino"] ?: "DETALLE_ALERTA"
        val idAlerta = data["id_alerta"]?.toLongOrNull()
        val idVehiculo = data["id_vehiculo"]?.toLongOrNull()
        val idNotificacion = data["id_notificacion"]?.toLongOrNull()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("abrir_desde_push", true)
            putExtra("destino_push", destino)
            if (idAlerta != null) putExtra("id_alerta", idAlerta)
            if (idVehiculo != null) putExtra("id_vehiculo", idVehiculo)
            if (idNotificacion != null) putExtra("id_notificacion", idNotificacion)
        }
        val pending = PendingIntent.getActivity(
            this,
            (idNotificacion ?: idAlerta ?: 0L).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val channelId = "mycar_alertas"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Alertas MyCar", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify((idNotificacion ?: System.currentTimeMillis()).toInt(), notif)
    }
}
