package backend.alertas.service;

import backend.alertas.dao.AlertasJdbcDao;
import backend.notificaciones.service.NotificacionInternaService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Núcleo RF23 reutilizable por el job interno (opcional) y por el endpoint de cron externo.
 * Idempotente: {@code sincronizar_alertas_vehiculo} + upsert avisos + outbox ON CONFLICT.
 * Extensible: futuras reglas SOAT/licencia se añaden aquí sin cambiar el Worker.
 */
@Service
public class EvaluacionProgramadaAlertasService {
    private static final Logger log = LoggerFactory.getLogger(EvaluacionProgramadaAlertasService.class);

    public enum Modo {
        /** Solo cuenta vehículos evaluables; no escribe alertas ni notificaciones. */
        SIMULAR,
        /** Misma lógica que el job diario RF23. */
        COMPLETO
    }

    public record Resultado(
            boolean omitidoEnCurso,
            String modo,
            int vehiculos,
            int cambiosEstado,
            int erroresVehiculo) {}

    private final AlertasJdbcDao alertas;
    private final ObjectProvider<NotificacionInternaService> notificaciones;
    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    public EvaluacionProgramadaAlertasService(
            AlertasJdbcDao alertas, ObjectProvider<NotificacionInternaService> notificaciones) {
        this.alertas = alertas;
        this.notificaciones = notificaciones;
    }

    public Resultado evaluar(Modo modo) {
        if (!enCurso.compareAndSet(false, true)) {
            log.info("RF23 evaluación omitida: ya hay una ejecución en curso");
            return new Resultado(true, modo.name(), 0, 0, 0);
        }
        try {
            List<AlertasJdbcDao.VehiculoActivo> vehiculos = alertas.listarVehiculosActivosConAlertasEvaluables();
            if (modo == Modo.SIMULAR) {
                log.info("RF23 evaluación SIMULAR: {} vehículos (sin escrituras)", vehiculos.size());
                return new Resultado(false, modo.name(), vehiculos.size(), 0, 0);
            }
            int totalCambios = 0;
            int errores = 0;
            for (AlertasJdbcDao.VehiculoActivo v : vehiculos) {
                try {
                    totalCambios += alertas.sincronizar(v.idVehiculo(), v.idUsuario());
                    var n = notificaciones.getIfAvailable();
                    if (n != null) {
                        n.sincronizarAvisosVehiculo(v.idVehiculo());
                    }
                    // Futuro: evaluadores SOAT / licencia por vehículo o por usuario,
                    // sin cambiar el contrato del Worker Cloudflare.
                } catch (RuntimeException ex) {
                    errores++;
                    log.warn("RF23 evaluación falló para vehículo {}: {}", v.idVehiculo(), ex.toString());
                }
            }
            log.info(
                    "RF23 evaluación COMPLETO: {} vehículos, {} cambios, {} errores",
                    vehiculos.size(),
                    totalCambios,
                    errores);
            return new Resultado(false, modo.name(), vehiculos.size(), totalCambios, errores);
        } finally {
            enCurso.set(false);
        }
    }
}
