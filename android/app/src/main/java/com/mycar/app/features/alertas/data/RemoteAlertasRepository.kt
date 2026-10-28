package com.mycar.app.features.alertas.data

import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.ContextoProgramarAlerta
import com.mycar.app.features.alertas.domain.DatosAlerta
import com.mycar.app.features.alertas.domain.ReferenciaMantenimientoAlerta
import com.mycar.app.features.alertas.domain.TipoAlerta
import java.util.UUID

class RemoteAlertasRepository(private val api: AlertasApi) : AlertasRepository {
    override suspend fun contexto(idVehiculo: Long): ContextoProgramarAlerta {
        val c = api.contexto(idVehiculo)
        return ContextoProgramarAlerta(
            c.fechaServidor.orEmpty(), c.kmActual, c.idLecturaActual,
            c.tipos.orEmpty().map { TipoAlerta(it.codigo.orEmpty(), it.nombre.orEmpty()) },
            c.referencias.orEmpty().map {
                ReferenciaMantenimientoAlerta(it.idMantenimiento ?: 0, it.codigoTipo.orEmpty(),
                    it.fechaServicio.orEmpty(), it.servicioOtro)
            },
            c.programacionDisponible == true
        )
    }

    override suspend fun listar(idVehiculo: Long): Pair<Int, List<DatosAlerta>> {
        val r = api.listar(idVehiculo)
        return (r.alertasSincronizadas ?: 0) to r.alertas.orEmpty().map {
            DatosAlerta(it.idAlerta ?: 0, it.nombreTipo.orEmpty(), it.criterio.orEmpty(), it.estado.orEmpty(),
                it.fechaObjetivo, it.kmObjetivo, it.servicioOtro)
        }
    }

    override suspend fun programar(idVehiculo: Long, solicitud: ProgramarAlertaDto): Long {
        val resp = api.programar(idVehiculo, solicitud.copy(idOperacion = solicitud.idOperacion.ifBlank {
            UUID.randomUUID().toString()
        }))
        return resp.idAlerta ?: error("Sin idAlerta")
    }
}
