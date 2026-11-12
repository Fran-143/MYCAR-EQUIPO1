package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.model.ErrorAlertas;
import backend.notificaciones.service.NotificacionInternaService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/** RF23: sincroniza antes del listado (función PostgreSQL). RF24: detalle con sync puntual. */
@Service
public class SincronizarAlertasService {
    private static final Set<String> ATENDIBLES = Set.of("PROXIMA", "PENDIENTE", "VENCIDA");
    private static final Set<String> ANULABLES = Set.of("PROGRAMADA", "PROXIMA", "PENDIENTE", "VENCIDA");

    private final AlertasJdbcDao alertas;
    private final ObjectProvider<NotificacionInternaService> notificaciones;

    public SincronizarAlertasService(AlertasJdbcDao alertas) {
        this(alertas, null);
    }

    @Autowired
    public SincronizarAlertasService(
            AlertasJdbcDao alertas, ObjectProvider<NotificacionInternaService> notificaciones) {
        this.alertas = alertas;
        this.notificaciones = notificaciones;
    }

    @Transactional
    public ContratoRf22.ListaResponse listarMisAlertas(long usuario, long vehiculo) {
        if (vehiculo <= 0) throw new ErrorAlertas("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
        exigirVehiculo(usuario, vehiculo);
        int cambios = sincronizarSeguro(vehiculo, usuario);
        return new ContratoRf22.ListaResponse(cambios, alertas.listar(vehiculo));
    }

    /** RF24 B1: consulta detalle, sincroniza estados del vehículo y expone acciones. */
    @Transactional
    public ContratoRf22.DetalleAlertaDto consultarDetalle(long usuario, long vehiculo, long idAlerta) {
        if (vehiculo <= 0 || idAlerta <= 0) {
            throw new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible.");
        }
        exigirVehiculo(usuario, vehiculo);
        sincronizarSeguro(vehiculo, usuario);
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

    private void exigirVehiculo(long usuario, long vehiculo) {
        try {
            alertas.exigirVehiculoPropioActivo(usuario, vehiculo);
        } catch (EmptyResultDataAccessException ex) {
            throw new ErrorAlertas("VEHICULO_NO_ENCONTRADO", "Vehículo no disponible.");
        }
    }

    private int sincronizarSeguro(long vehiculo, long usuario) {
        try {
            int cambios = alertas.sincronizar(vehiculo, usuario);
            // RF23: sincronizar avisos internos idempotentes tras transiciones reales.
            if (notificaciones != null) {
                var n = notificaciones.getIfAvailable();
                if (n != null) {
                    n.sincronizarAvisosVehiculo(vehiculo);
                }
            }
            return cambios;
        } catch (EmptyResultDataAccessException ex) {
            throw new ErrorAlertas("VEHICULO_NO_ENCONTRADO", "Vehículo no disponible.");
        }
    }
}
