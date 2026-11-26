package backend.alertas.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CronSecretAuthenticator {
    private final CronSecretProperties props;

    public CronSecretAuthenticator(CronSecretProperties props) {
        this.props = props;
    }

    /** Comparación en tiempo constante. Nunca acepta secreto vacío configurado. */
    public boolean acepta(String proporcionado) {
        String esperado = props.getSecret();
        if (!StringUtils.hasText(esperado) || !StringUtils.hasText(proporcionado)) {
            return false;
        }
        byte[] a = esperado.getBytes(StandardCharsets.UTF_8);
        byte[] b = proporcionado.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
