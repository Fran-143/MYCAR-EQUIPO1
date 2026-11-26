package backend.fotos;

import backend.documentos.FakeAlmacenamientoDocumentos;
import backend.documentos.SupabaseAlmacenamientoDocumentos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
public class FotosConfiguracion {

    @Bean
    @ConditionalOnProperty(prefix = "mycar.fotos", name = "modo", havingValue = "fake", matchIfMissing = true)
    AlmacenamientoFotos fakeFotos() {
        String override = System.getenv("MYCAR_FAKE_FOTOS_DIR");
        if (override != null && !override.isBlank()) {
            return new AlmacenamientoFotos(new FakeAlmacenamientoDocumentos(Path.of(override)));
        }
        String local = System.getenv("LOCALAPPDATA");
        Path root = (local != null && !local.isBlank())
                ? Path.of(local, "MyCar", "desarrollo-local", "fotos-fake")
                : Path.of(System.getProperty("java.io.tmpdir"), "mycar-fotos-fake");
        return new AlmacenamientoFotos(new FakeAlmacenamientoDocumentos(root));
    }

    @Bean
    @ConditionalOnProperty(prefix = "mycar.fotos", name = "modo", havingValue = "supabase")
    AlmacenamientoFotos supabaseFotos(
            @Value("${mycar.fotos.supabase.url}") String url,
            @Value("${mycar.fotos.supabase.bucket:mycar-fotos-privadas}") String bucket,
            @Value("${mycar.fotos.supabase.secret}") String secret,
            ObjectMapper json
    ) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("mycar.fotos.supabase.url es obligatorio en modo=supabase");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("mycar.fotos.supabase.secret es obligatorio en modo=supabase");
        }
        if (!(secret.startsWith("sb_secret_") || secret.startsWith("eyJ"))) {
            throw new IllegalStateException(
                    "Secret Supabase inválido para fotos (se espera sb_secret_* o JWT service_role)");
        }
        return new AlmacenamientoFotos(new SupabaseAlmacenamientoDocumentos(url, bucket, secret, json));
    }
}
