package backend.recordatorioskm.service;

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
    private final EvaluacionRecordatoriosKmService evaluacion;

    public EvaluacionDiariaRecordatoriosKmJob(EvaluacionRecordatoriosKmService evaluacion) {
        this.evaluacion = evaluacion;
    }

    @Scheduled(cron = "${mycar.rf43.evaluacion-cron:0 30 4 * * *}", zone = "America/Lima")
    public void ejecutar() {
        evaluacion.evaluarPendientes();
    }
}
