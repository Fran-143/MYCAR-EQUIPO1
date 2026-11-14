package backend.talleres.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Secreto de administración de catálogo oficial (no JWT de usuario). */
@Component
public class TallerAdminSecretAuthenticator {
    private final String secreto;

    public TallerAdminSecretAuthenticator(
            @Value("${mycar.taller-admin.secret:}") String secreto) {
        this.secreto = secreto == null ? "" : secreto.trim();
    }

    public boolean acepta(String header) {
        if (secreto.isEmpty() || header == null || header.isBlank()) return false;
        byte[] a = secreto.getBytes(StandardCharsets.UTF_8);
        byte[] b = header.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    public boolean configurado() {
        return !secreto.isEmpty();
    }
}
