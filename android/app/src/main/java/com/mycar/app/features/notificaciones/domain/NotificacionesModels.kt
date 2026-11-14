package com.mycar.app.features.notificaciones.domain

data class NotificacionItem(
    val id: Long,
    val categoria: String,
    val estadoGestion: String,
    val leida: Boolean,
    val titulo: String,
    val cuerpo: String,
    val destino: String,
    val idAlerta: Long?,
    val idVehiculo: Long?,
    val fechaEmisionUtc: String,
    /** Estado RF23 de la alerta; distinto de estadoGestion (bandeja). */
    val estadoAlerta: String? = null
)

data class BandejaNotificaciones(
    val pestana: String,
    val categoria: String,
    val filtroLectura: String,
    val contadorPendientesGlobal: Int,
    val contadorNoLeidasGlobal: Int,
    val items: List<NotificacionItem>
)

data class ResultadoLectura(
    val id: Long,
    val leida: Boolean,
    val estadoGestion: String,
    val destino: String,
    val idAlerta: Long?,
    val idVehiculo: Long?
)

interface NotificacionesRepository {
    suspend fun consultarBandeja(
        pestana: String,
        categoria: String,
        filtroLectura: String
    ): BandejaNotificaciones

    suspend fun marcarLeida(idNotificacion: Long): ResultadoLectura
}
