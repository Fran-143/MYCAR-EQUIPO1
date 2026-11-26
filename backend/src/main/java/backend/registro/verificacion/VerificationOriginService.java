package backend.registro.verificacion;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Emite credenciales de origen (instalación) para el flujo RF01. */
public final class VerificationOriginService {
    private final VerificationRepository db;
    private final VerificationCrypto crypto;
    private final SecureRandom random = new SecureRandom();

    public VerificationOriginService(VerificationRepository db, VerificationCrypto crypto) {
        this.db = db;
        this.crypto = crypto;
    }

    public VerificationService.Origin create() {
        UUID id = UUID.randomUUID();
        byte[] secretBytes = new byte[32];
        random.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        byte[] subject = new byte[32];
        random.nextBytes(subject);
        Instant now = db.now();
        db.tx(() -> {
            db.update("""
                INSERT INTO mycar.control_verificacion(
                  id_control, finalidad, tipo, clave_sujeto, credencial_hash, creado_utc, ultimo_uso_utc)
                VALUES (?,'REGISTRO','ORIGEN',?,?,?,?)
                """, id, subject, crypto.credential(secret),
                    VerificationRepository.at(now), VerificationRepository.at(now));
            return null;
        });
        return new VerificationService.Origin(id, secret);
    }
}
