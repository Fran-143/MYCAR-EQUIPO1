package backend.autenticacion;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Lock order for commands: user first, then session. All writes share the caller's transaction. */
public interface AuthenticationStore {
    record Account(long id,String hash,String state,Instant blockedUntil) {
        @Override public String toString(){return "Account[REDACTED]";}
    }
    record Session(long id,long userId,UUID installation,String refreshHash,String state,
                   Instant expiresAt,Instant revokedAt,String accountState) {
        @Override public String toString(){return "Session[REDACTED]";}
        public boolean isValidAt(Instant now) {
            return "ACTIVA".equals(accountState) && "VIGENTE".equals(state)
                    && revokedAt==null && now.isBefore(expiresAt);
        }
    }
    Instant now();
    Optional<Account> lockAccount(String normalizedEmail);
    Optional<Account> lockAccount(long userId);
    void recordFailure(long userId);
    void resetFailures(long userId);
    void replaceInstallation(long userId,UUID installation,Instant now);
    long createSession(long userId,UUID installation,String refreshHash,Instant expiresAt,Instant now);
    Optional<Session> findByRefreshHash(String hash);
    Optional<Session> lockSession(long id);
    Optional<Session> findSession(long id);
    void rotateRefresh(long id,String hash,Instant now);
}
