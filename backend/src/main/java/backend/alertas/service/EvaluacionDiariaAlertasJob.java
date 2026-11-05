package backend.alertas.service;

import backend.alertas.dao.AlertasJdbcDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public EvaluacionDiariaAlertasJob(AlertasJdbcDao alertas) {
        this.alertas = alertas;
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
            } catch (RuntimeException ex) {
                log.warn("RF23 evaluación diaria falló para vehículo {}: {}", v.idVehiculo(), ex.toString());
            }
        }
        log.info("RF23 evaluación diaria: {} vehículos, {} cambios de estado", vehiculos.size(), totalCambios);
    }
}
