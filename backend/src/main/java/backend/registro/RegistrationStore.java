package backend.registro;

import java.time.Instant;

/** Persistence port; implementations participate in the caller's transaction. */
public interface RegistrationStore {
    boolean emailExists(String normalizedEmail);
    long createAccount(RegistrationData data, String passwordHash, Instant acceptedAt);
}
