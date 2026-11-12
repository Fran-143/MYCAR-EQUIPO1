package backend.push.worker;

import backend.push.service.OutboxPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Procesa outbox_push. Respetar RF48: si preferencias/push deshabilitado a nivel app,
 * el worker puede apagarse con mycar.fcm.worker-habilitado=false.
 */
@Component
@ConditionalOnProperty(prefix = "mycar.fcm", name = "worker-habilitado", havingValue = "true", matchIfMissing = true)
public class OutboxPushWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxPushWorker.class);
    private final OutboxPushService outbox;
    private final int lote;

    public OutboxPushWorker(
            OutboxPushService outbox,
            @Value("${mycar.fcm.worker-lote:25}") int lote) {
        this.outbox = outbox;
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${mycar.fcm.worker-delay-ms:15000}")
    public void tick() {
        int n = outbox.procesarLote(lote);
        if (n > 0) {
            log.info("Outbox push: {} enviados", n);
        }
    }
}
