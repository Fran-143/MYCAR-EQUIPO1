package backend.crypto;

import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DatosSensiblesConfiguracion {

    @Bean
    DatosSensiblesCryptoGate datosSensiblesCryptoGate(
            @Value("${mycar.datos-sensibles.enabled:false}") boolean enabled,
            @Value("${mycar.datos-sensibles.key-id:v1}") String keyId,
            @Value("${mycar.datos-sensibles.aes-key:}") String aesB64,
            @Value("${mycar.datos-sensibles.blind-key:}") String blindB64
    ) {
        if (!enabled) {
            return DatosSensiblesCryptoGate.deshabilitado();
        }
        if (aesB64 == null || aesB64.isBlank() || blindB64 == null || blindB64.isBlank()) {
            throw new IllegalStateException(
                    "mycar.datos-sensibles.enabled=true requiere MYCAR_DATOS_SENSIBLES_AES_KEY y BLIND_KEY");
        }
        byte[] aes = Base64.getDecoder().decode(aesB64.trim());
        byte[] blind = Base64.getDecoder().decode(blindB64.trim());
        return DatosSensiblesCryptoGate.habilitado(new DatosSensiblesCrypto(aes, blind, keyId));
    }
}
