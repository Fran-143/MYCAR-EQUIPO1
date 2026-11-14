package backend.documentos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DocumentosConfiguracion {

    @Bean
    @ConditionalOnProperty(prefix = "mycar.documentos", name = "modo", havingValue = "fake")
    AlmacenamientoDocumentos fakeDocumentos() {
        return new FakeAlmacenamientoDocumentos();
    }

    @Bean
    @ConditionalOnProperty(prefix = "mycar.documentos.supabase", name = {"url", "secret"})
    AlmacenamientoDocumentos supabaseDocumentos(
            @Value("${mycar.documentos.supabase.url}") String url,
            @Value("${mycar.documentos.supabase.bucket:mycar-documentos-privados}") String bucket,
            @Value("${mycar.documentos.supabase.secret}") String secret,
            ObjectMapper json
    ) {
        if (secret == null || !secret.startsWith("sb_secret_")) {
            throw new IllegalStateException("Secret Supabase inválido (se espera prefijo sb_secret_)");
        }
        return new SupabaseAlmacenamientoDocumentos(url, bucket, secret, json);
    }

    @Bean
    @ConditionalOnMissingBean(AlmacenamientoDocumentos.class)
    AlmacenamientoDocumentos almacenamientoPorDefecto() {
        return new AlmacenamientoNoConfigurado();
    }
}