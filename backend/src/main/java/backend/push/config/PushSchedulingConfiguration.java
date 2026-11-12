package backend.push.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling independiente de RF23 (apagado en Render Free).
 * Necesario para OutboxPushWorker aunque evaluacion-diaria esté en false.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "mycar.fcm", name = "worker-habilitado", havingValue = "true", matchIfMissing = true)
public class PushSchedulingConfiguration {
}
