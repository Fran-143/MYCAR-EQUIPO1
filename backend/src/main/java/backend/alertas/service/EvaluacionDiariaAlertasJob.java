package backend.alertas.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RF23 — disparador diario America/Lima (job interno Spring).
 * En Render/Neon permanece desactivado ({@code evaluacion-diaria-habilitada=false});
 * el cron externo Cloudflare usa {@link EvaluacionProgramadaAlertasService} vía endpoint.
 */
@Component
@ConditionalOnProperty(
        prefix = "mycar.rf23",
        name = "evaluacion-diaria-habilitada",
        havingValue = "true",
        matchIfMissing = true)
public class EvaluacionDiariaAlertasJob {
    private final EvaluacionProgramadaAlertasService evaluacion;

    public EvaluacionDiariaAlertasJob(EvaluacionProgramadaAlertasService evaluacion) {
        this.evaluacion = evaluacion;
    }

    /** Cron en America/Lima: configurable; por defecto 03:15. */
    @Scheduled(cron = "${mycar.rf23.evaluacion-diaria-cron:0 15 3 * * *}", zone = "America/Lima")
    public void evaluarDiario() {
        evaluacion.evaluar(EvaluacionProgramadaAlertasService.Modo.COMPLETO);
    }
}
