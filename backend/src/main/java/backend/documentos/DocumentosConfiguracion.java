package backend.documentos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration(proxyBeanMethods = false)
public class DocumentosConfiguracion {

    /** Tests / sin credenciales: almacenamiento en memoria. */
    @Bean
    @ConditionalOnProperty(prefix = "mycar.documentos", name = "modo", havingValue = "fake", matchIfMissing = false)
    AlmacenamientoDocumentos fakeDocumentos() {
        return new FakeAlmacenamientoDocumentos();
    }

    /**
     * Storage real. Activar con {@code mycar.documentos.modo=supabase}
     * y URL + secret ({@code sb_secret_…} o service_role JWT legacy).
     * Lazy: no materializar HttpClient/SDK en el camino crítico hasta Tomcat.
     */
    @Bean
    @Lazy
    @ConditionalOnProperty(prefix = "mycar.documentos", name = "modo", havingValue = "supabase")
    AlmacenamientoDocumentos supabaseDocumentos(
            @Value("${mycar.documentos.supabase.url}") String url,
            @Value("${mycar.documentos.supabase.bucket:mycar-documentos-privados}") String bucket,
            @Value("${mycar.documentos.supabase.secret}") String secret,
            ObjectMapper json
    ) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("mycar.documentos.supabase.url es obligatorio en modo=supabase");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("mycar.documentos.supabase.secret es obligatorio en modo=supabase");
        }
        if (!(secret.startsWith("sb_secret_") || secret.startsWith("eyJ"))) {
            throw new IllegalStateException(
                    "Secret Supabase inválido (se espera sb_secret_* o JWT service_role)");
        }
        return new SupabaseAlmacenamientoDocumentos(url, bucket, secret, json);
    }

    @Bean
    @ConditionalOnMissingBean(AlmacenamientoDocumentos.class)
    AlmacenamientoDocumentos almacenamientoPorDefecto() {
        return new AlmacenamientoNoConfigurado();
    }
}
