package backend.recuperacion;

import java.time.Instant;
import java.util.Optional;

public interface RecoveryStore {
    Instant now();

    Optional<Account> findByEmail(String emailNormalized);

    void invalidateActive(long userId);

    long insertCode(long userId, String codeHash, Instant expiresAt, Instant now);

    Optional<CodeRow> lockLatestActive(long userId);

    /** Último código del usuario (cualquier estado), para cooldown de reenvío. */
    Optional<CodeRow> findLatest(long userId);

    void markConsumed(long codeId, Instant now);

    void markInvalidated(long codeId);

    void incrementFailures(long codeId);

    int updatePassword(long userId, String passwordHash);

    /** Bloquea la fila de usuario ACTIVA y devuelve el hash vigente (para no reutilizar la misma clave). */
    Optional<String> lockPasswordHash(long userId);

    record Account(long userId, String email, String estadoCuenta, String passwordHash) { }

    record CodeRow(long id, long userId, String codeHash, String estado, Instant expiresAt, Instant createdAt,
            int failures) { }
}
