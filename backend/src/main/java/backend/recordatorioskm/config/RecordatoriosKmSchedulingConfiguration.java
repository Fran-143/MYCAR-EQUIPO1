package backend.recordatorioskm.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** RF43: habilita @Scheduled del job de recordatorios de kilometraje. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "mycar.rf43",
        name = "evaluacion-habilitada",
        havingValue = "true",
        matchIfMissing = true)
public class RecordatoriosKmSchedulingConfiguration {
}
