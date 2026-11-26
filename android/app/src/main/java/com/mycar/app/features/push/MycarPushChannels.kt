package com.mycar.app.features.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Canal FCM de alertas: debe existir antes de que el sistema muestre un push en segundo plano. */
object MycarPushChannels {
    const val ALERTAS_ID = "mycar_alertas"
    const val ALERTAS_NOMBRE = "Alertas MyCar"

    fun asegurarCanalAlertas(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val existente = nm.getNotificationChannel(ALERTAS_ID)
        if (existente != null && existente.importance >= NotificationManager.IMPORTANCE_HIGH) return
        if (existente != null) {
            nm.deleteNotificationChannel(ALERTAS_ID)
        }
        nm.createNotificationChannel(
            NotificationChannel(
                ALERTAS_ID,
                ALERTAS_NOMBRE,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recordatorios de mantenimiento, SOAT y kilometraje"
                enableVibration(true)
            }
        )
    }
}
