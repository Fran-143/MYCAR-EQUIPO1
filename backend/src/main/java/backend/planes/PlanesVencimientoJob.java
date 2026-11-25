package backend.planes;

import backend.planes.service.PlanesService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Vence Premium caducados en local. En Neon/Render dejar habilitado=false
 * (no hay migración remota ni sandbox productivo en este RF).
 */
@Component
@ConditionalOnProperty(name = "mycar.planes.vencimiento-job.enabled", havingValue = "true")
public class PlanesVencimientoJob {
    private final PlanesService service;

    public PlanesVencimientoJob(PlanesService service) {
        this.service = service;
    }

    @Scheduled(cron = "${mycar.planes.vencimiento-job.cron:0 20 3 * * *}", zone = "America/Lima")
    public void vencer() {
        service.procesarVencimientosPendientes();
    }
}
