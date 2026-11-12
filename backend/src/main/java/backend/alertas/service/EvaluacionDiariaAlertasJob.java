package backend.alertas.service;

import backend.alertas.dao.AlertasJdbcDao;
import backend.notificaciones.service.NotificacionInternaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RF23 — disparador diario America/Lima. Reutiliza {@code sincronizar_alertas_vehiculo}.
 * El listado puntual al abrir Mis alertas se conserva en {@link SincronizarAlertasService}.
 */
@Component
@ConditionalOnProperty(prefix = "mycar.rf23", name = "evaluacion-diaria-habilitada", havingValue = "true",
        matchIfMissing = true)
public class EvaluacionDiariaAlertasJob {
    private static final Logger log = LoggerFactory.getLogger(EvaluacionDiariaAlertasJob.class);
    private final AlertasJdbcDao alertas;
    private final ObjectProvider<NotificacionInternaService> notificaciones;

    public EvaluacionDiariaAlertasJob(AlertasJdbcDao alertas) {
        this(alertas, null);
    }

    @Autowired
    public EvaluacionDiariaAlertasJob(
            AlertasJdbcDao alertas, ObjectProvider<NotificacionInternaService> notificaciones) {
        this.alertas = alertas;
        this.notificaciones = notificaciones;
    }

    /** Cron en America/Lima: 03:15 cada día. */
    @Scheduled(cron = "${mycar.rf23.evaluacion-diaria-cron:0 15 3 * * *}", zone = "America/Lima")
    @Transactional
    public void evaluarDiario() {
        List<AlertasJdbcDao.VehiculoActivo> vehiculos = alertas.listarVehiculosActivosConAlertasEvaluables();
        int totalCambios = 0;
        for (AlertasJdbcDao.VehiculoActivo v : vehiculos) {
            try {
                totalCambios += alertas.sincronizar(v.idVehiculo(), v.idUsuario());
                if (notificaciones != null) {
                    var n = notificaciones.getIfAvailable();
                    if (n != null) {
                        n.sincronizarAvisosVehiculo(v.idVehiculo());
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("RF23 evaluación diaria falló para vehículo {}: {}", v.idVehiculo(), ex.toString());
            }
        }
        log.info("RF23 evaluación diaria: {} vehículos, {} cambios de estado", vehiculos.size(), totalCambios);
    }
}
