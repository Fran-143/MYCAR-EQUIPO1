package backend.recordatorioskm.service;

import backend.push.service.RecordatorioPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "mycar.rf43",
        name = "evaluacion-habilitada",
        havingValue = "true",
        matchIfMissing = true)
public class EvaluacionDiariaRecordatoriosKmJob {
    private static final Logger log = LoggerFactory.getLogger(EvaluacionDiariaRecordatoriosKmJob.class);

    private final EvaluacionRecordatoriosKmService evaluacion;
    private final ObjectProvider<RecordatorioPushService> pushRecordatorios;

    public EvaluacionDiariaRecordatoriosKmJob(
            EvaluacionRecordatoriosKmService evaluacion,
            ObjectProvider<RecordatorioPushService> pushRecordatorios) {
        this.evaluacion = evaluacion;
        this.pushRecordatorios = pushRecordatorios;
    }

    @Scheduled(cron = "${mycar.rf43.evaluacion-cron:0 30 4 * * *}", zone = "America/Lima")
    public void ejecutar() {
        int emitidas = evaluacion.evaluarPendientes();
        if (emitidas <= 0) {
            return;
        }
        var push = pushRecordatorios.getIfAvailable();
        if (push == null) {
            return;
        }
        try {
            var r = push.encolarCandidatosTrasEvaluacion();
            log.info(
                    "RF43 job→push encolado: emitidas={} encolados={} omitRf48={}",
                    emitidas, r.encolados(), r.omitidosRf48());
        } catch (Exception ex) {
            log.warn("RF43 job→push encolado falló: {}", ex.toString());
        }
    }
}
