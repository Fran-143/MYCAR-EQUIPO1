package backend.alertas.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** RF23: evaluación diaria opcional; no reemplaza el sync al abrir Mis alertas. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "mycar.rf23", name = "evaluacion-diaria-habilitada", havingValue = "true",
        matchIfMissing = true)
public class AlertasSchedulingConfiguration {
}
