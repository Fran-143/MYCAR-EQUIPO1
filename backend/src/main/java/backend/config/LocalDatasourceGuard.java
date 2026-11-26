package backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Locale;

/**
 * Impide que el perfil local (sin neon/render) use accidentalmente Neon u otro host remoto.
 * No aplica cuando SPRING_PROFILES_ACTIVE incluye neon o render.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LocalDatasourceGuard {
    private static final Logger log = LoggerFactory.getLogger(LocalDatasourceGuard.class);

    private final Environment env;
    private final String datasourceUrl;
    private final boolean enforceLocal;

    public LocalDatasourceGuard(
            Environment env,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${mycar.seguridad.exigir-datasource-local:true}") boolean enforceLocal) {
        this.env = env;
        this.datasourceUrl = datasourceUrl == null ? "" : datasourceUrl;
        this.enforceLocal = enforceLocal;
    }

    @PostConstruct
    public void verify() {
        String[] profiles = env.getActiveProfiles();
        boolean prodLike = Arrays.stream(profiles)
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(p -> p.equals("neon") || p.equals("render"));
        if (prodLike || !enforceLocal) {
            log.info("datasource_guard omitido profiles={}", Arrays.toString(profiles));
            return;
        }
        String url = datasourceUrl.toLowerCase(Locale.ROOT);
        boolean local = url.contains("127.0.0.1") || url.contains("localhost");
        boolean neon = url.contains("neon.tech") || url.contains("neon.tech".toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT));
        boolean remoteAws = url.contains("amazonaws.com") || url.contains("aws.neon");
        if (!local || neon || remoteAws) {
            throw new IllegalStateException(
                    "Arranque local bloqueado: spring.datasource.url debe ser PostgreSQL local "
                            + "(127.0.0.1/localhost). Quita SPRING_DATASOURCE_URL de Neon del entorno "
                            + "o usa el perfil neon/render de forma explícita.");
        }
        if (!url.contains("mycar_mvp") && !url.contains("/mycar")) {
            log.warn("datasource_guard: URL local sin mycar_mvp explícito; revisar configuración.");
        }
        log.info("datasource_guard OK host_local=true");
    }
}
