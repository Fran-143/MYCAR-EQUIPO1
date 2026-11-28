package backend.recordatorioskm.service;

import backend.recordatorioskm.dao.RecordatorioKmJdbcDao;
import backend.recordatorioskm.domain.FrecuenciaRecordatorioKm;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EA3: emite notificaciones internas de kilometraje según la frecuencia del select
 * (config_recordatorio_km.frecuencia). No crea alertas. El encolado FCM lo hace el
 * controlador/cron o el job diario tras {@link #evaluarPendientes()}.
 */
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
        LocalDate hoyLima = RecordatorioKmJdbcDao.aFechaLima(ahora);
        int emitidas = 0;
        for (var row : dao.listarPendientesJob(ahora)) {
            if (row.proximoEnvio() == null) {
                continue;
            }
            var frecuencia = FrecuenciaRecordatorioKm.parse(
                    row.frecuencia() == null ? "INTERDIARIO" : row.frecuencia()).orElse(null);
            if (frecuencia == null) {
                continue;
            }
            LocalDate ciclo = RecordatorioKmJdbcDao.aFechaLima(row.proximoEnvio());
            String clave = "KM:" + row.idVehiculo() + ":" + ciclo + ":" + frecuencia.codigo();
            String titulo = "Actualiza el kilometraje de tu vehículo";
            String cuerpo = "Es momento de actualizar el kilometraje de tu "
                    + row.marca() + " " + row.modelo() + " en MyCar";
            boolean creada = dao.insertarNotificacionKm(
                    row.idUsuario(), row.idVehiculo(), titulo, cuerpo, clave);
            // Siempre avanzar el calendario con la frecuencia del select (aunque la notif
            // del ciclo ya existiera): evita quedar atascado en el mismo proximo_envio.
            Instant siguiente = siguienteEnvioSegunFrecuencia(frecuencia, hoyLima);
            dao.marcarEnvioYProgramarSiguiente(row.idVehiculo(), ahora, siguiente);
            if (creada) {
                emitidas++;
            }
        }
        if (emitidas > 0) {
            log.info("RF43: {} recordatorio(s) de kilometraje emitidos según frecuencia", emitidas);
        }
        return emitidas;
    }

    /**
     * Próximo disparo = frecuencia.calcularProximo(hoy), avanzando hasta quedar en el futuro.
     * Así DIARIO → mañana; SEMANAL → +7; etc., alineado al select del vehículo.
     */
    static Instant siguienteEnvioSegunFrecuencia(FrecuenciaRecordatorioKm frecuencia, LocalDate hoyLima) {
        LocalDate siguiente = frecuencia.calcularProximo(hoyLima);
        int guard = 0;
        while (!siguiente.isAfter(hoyLima) && guard++ < 400) {
            siguiente = frecuencia.calcularProximo(siguiente);
        }
        return RecordatorioKmJdbcDao.inicioDiaLimaUtc(siguiente);
    }
}
