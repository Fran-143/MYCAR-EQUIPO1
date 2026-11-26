package backend.recordatorioskm.service;

import backend.recordatorioskm.dao.RecordatorioKmJdbcDao;
import backend.recordatorioskm.domain.FrecuenciaRecordatorioKm;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** EA3: emite notificaciones internas de kilometraje. No crea alertas. */
@Service
public class EvaluacionRecordatoriosKmService {
    private static final Logger log = LoggerFactory.getLogger(EvaluacionRecordatoriosKmService.class);

    private final RecordatorioKmJdbcDao dao;

    public EvaluacionRecordatoriosKmService(RecordatorioKmJdbcDao dao) {
        this.dao = dao;
    }

    /** Cuenta candidatos sin escribir (modo SIMULAR del cron). */
    @Transactional(readOnly = true)
    public int contarCandidatos() {
        return dao.listarPendientesJob(Instant.now()).size();
    }

    @Transactional
    public int evaluarPendientes() {
        Instant ahora = Instant.now();
        int emitidas = 0;
        for (var row : dao.listarPendientesJob(ahora)) {
            if (row.proximoEnvio() == null) {
                continue;
            }
            LocalDate ciclo = RecordatorioKmJdbcDao.aFechaLima(row.proximoEnvio());
            String freq = row.frecuencia() == null ? "INTERDIARIO" : row.frecuencia();
            if (FrecuenciaRecordatorioKm.parse(freq).isEmpty()) {
                continue;
            }
            String clave = "KM:" + row.idVehiculo() + ":" + ciclo + ":" + freq;
            String titulo = "Actualiza el kilometraje de tu vehículo";
            String cuerpo = "Es momento de actualizar el kilometraje de tu "
                    + row.marca() + " " + row.modelo() + " en MyCar";
            boolean creada = dao.insertarNotificacionKm(
                    row.idUsuario(), row.idVehiculo(), titulo, cuerpo, clave);
            if (creada) {
                dao.marcarUltimoEnvio(row.idVehiculo(), ahora);
                emitidas++;
            }
        }
        if (emitidas > 0) {
            log.info("RF43: {} recordatorio(s) de kilometraje emitidos", emitidas);
        }
        return emitidas;
    }
}
