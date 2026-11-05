package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.model.ErrorAlertas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/** RF23: sincroniza antes del listado (función PostgreSQL). RF24: detalle con sync puntual. */
@Service
public class SincronizarAlertasService {
    private static final Set<String> ATENDIBLES = Set.of("PROXIMA", "PENDIENTE", "VENCIDA");
    private static final Set<String> ANULABLES = Set.of("PROGRAMADA", "PROXIMA", "PENDIENTE", "VENCIDA");

    private final AlertasJdbcDao alertas;

    public SincronizarAlertasService(AlertasJdbcDao alertas) {
        this.alertas = alertas;
    }

    @Transactional
    public ContratoRf22.ListaResponse listarMisAlertas(long usuario, long vehiculo) {
        if (vehiculo <= 0) throw new ErrorAlertas("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
        alertas.exigirVehiculoPropioActivo(usuario, vehiculo);
        int cambios = alertas.sincronizar(vehiculo, usuario);
        return new ContratoRf22.ListaResponse(cambios, alertas.listar(vehiculo));
    }

    /** RF24 B1: consulta detalle, sincroniza estados del vehículo y expone acciones. */
    @Transactional
    public ContratoRf22.DetalleAlertaDto consultarDetalle(long usuario, long vehiculo, long idAlerta) {
        if (vehiculo <= 0 || idAlerta <= 0) {
            throw new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible.");
        }
        alertas.exigirVehiculoPropioActivo(usuario, vehiculo);
        alertas.sincronizar(vehiculo, usuario);
        var base = alertas.obtenerDetallePropio(usuario, vehiculo, idAlerta)
                .orElseThrow(() -> new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible."));
        var acciones = new ContratoRf22.AccionesDetalleDto(
                ATENDIBLES.contains(base.estado()),
                ANULABLES.contains(base.estado()));
        return new ContratoRf22.DetalleAlertaDto(
                base.idAlerta(), "MANTENIMIENTO", base.codigoTipo(), base.nombreTipo(),
                base.criterio(), base.origenCalculo(), base.estado(), base.fechaEstadoUtc(),
                base.fechaObjetivo(), base.kmObjetivo(), base.kmBase(), base.intervaloKm(),
                base.unidadTiempo(), base.cantidadTiempo(), base.fechaBase(), base.servicioOtro(),
                base.idTipoMantenimiento(), base.idMantenimientoReferencia(), base.idMantenimientoAtencion(),
                base.versionFila(),
                new ContratoRf22.VehiculoResumenDto(
                        base.idVehiculo(), base.placa(), base.marca(), base.modelo(), base.estadoVehiculo()),
                acciones);
    }
}
