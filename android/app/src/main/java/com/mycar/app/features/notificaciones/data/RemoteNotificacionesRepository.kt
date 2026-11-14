package com.mycar.app.features.notificaciones.data

import com.google.gson.Gson
import com.mycar.app.features.notificaciones.domain.BandejaNotificaciones
import com.mycar.app.features.notificaciones.domain.NotificacionItem
import com.mycar.app.features.notificaciones.domain.NotificacionesRepository
import com.mycar.app.features.notificaciones.domain.ResultadoLectura
import java.io.IOException
import retrofit2.HttpException

class RemoteNotificacionesRepository(private val api: NotificacionesApi) : NotificacionesRepository {
    private val gson = Gson()

    override suspend fun consultarBandeja(
        pestana: String,
        categoria: String,
        filtroLectura: String
    ): BandejaNotificaciones {
        try {
            val r = api.bandeja(pestana, categoria, filtroLectura)
            return BandejaNotificaciones(
                pestana = r.pestana.orEmpty().ifBlank { pestana },
                categoria = r.categoria.orEmpty().ifBlank { categoria },
                filtroLectura = r.filtroLectura.orEmpty().ifBlank { filtroLectura },
                contadorPendientesGlobal = r.contadorPendientesGlobal ?: 0,
                contadorNoLeidasGlobal = r.contadorNoLeidasGlobal ?: 0,
                items = r.notificaciones.orEmpty().mapNotNull { mapItem(it) }
            )
        } catch (e: Exception) {
            throw IOException(mensajeHttp(e, "No se pudieron cargar las notificaciones"), e)
        }
    }

    override suspend fun marcarLeida(idNotificacion: Long): ResultadoLectura {
        try {
            val r = api.leer(idNotificacion)
            val id = r.idNotificacion ?: throw IOException("Respuesta de lectura incompleta")
            return ResultadoLectura(
                id = id,
                leida = r.leida == true,
                estadoGestion = r.estadoGestion.orEmpty(),
                destino = r.destino.orEmpty(),
                idAlerta = r.idAlerta,
                idVehiculo = r.idVehiculo
            )
        } catch (e: Exception) {
            throw IOException(mensajeHttp(e, "No se pudo abrir la notificación"), e)
        }
    }

    private fun mapItem(d: NotificacionDto): NotificacionItem? {
        val id = d.idNotificacion?.takeIf { it > 0 } ?: return null
        return NotificacionItem(
            id = id,
            categoria = d.categoria.orEmpty().ifBlank { "MANTENIMIENTO" },
            estadoGestion = d.estadoGestion.orEmpty(),
            leida = d.leida == true,
            titulo = d.titulo.orEmpty(),
            cuerpo = d.cuerpo.orEmpty(),
            destino = d.destino.orEmpty().ifBlank { "DETALLE_ALERTA" },
            idAlerta = d.idAlerta,
            idVehiculo = d.idVehiculo,
            fechaEmisionUtc = d.fechaEmisionUtc.orEmpty(),
            estadoAlerta = d.estadoAlerta?.takeIf { it.isNotBlank() }
        )
    }

    private fun mensajeHttp(e: Exception, fallback: String): String {
        val http = e as? HttpException ?: (e.cause as? HttpException) ?: return fallback
        val cuerpo = http.response()?.errorBody()?.string()?.let { texto ->
            runCatching { gson.fromJson(texto, ErrorNotificacionDto::class.java) }.getOrNull()
        }
        return cuerpo?.mensaje?.takeIf { it.isNotBlank() } ?: fallback
    }
}
