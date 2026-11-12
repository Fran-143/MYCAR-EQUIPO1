package backend.push.worker;

import backend.push.horario.VentanaPushLima;
import backend.push.service.OutboxPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drain de {@code outbox_push} (MANTENIMIENTO, SOAT, KILOMETRAJE, LICENCIA).
 * Solo dentro de 05:00–21:59 Lima para no enviar de noche.
 * En transición: mantener habilitado hasta validar cron 05:00; luego apagar.
 */
@Component
@ConditionalOnProperty(prefix = "mycar.fcm", name = "worker-habilitado", havingValue = "true", matchIfMissing = true)
public class OutboxPushWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxPushWorker.class);
    private final OutboxPushService outbox;
    private final VentanaPushLima ventana;
    private final int lote;

    public OutboxPushWorker(
            OutboxPushService outbox,
            VentanaPushLima ventana,
            @Value("${mycar.fcm.worker-lote:25}") int lote) {
        this.outbox = outbox;
        this.ventana = ventana;
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${mycar.fcm.worker-delay-ms:15000}")
    public void tick() {
        if (!ventana.enVentanaDespacho()) {
            return;
        }
        int n = outbox.procesarLote(lote);
        if (n > 0) {
            log.info("Outbox push drain: {} enviados", n);
        }
    }
}
