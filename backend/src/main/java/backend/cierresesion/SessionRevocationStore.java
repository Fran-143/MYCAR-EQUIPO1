package backend.cierresesion;

import java.time.Instant;
import java.util.UUID;

/** Writes participate in the caller's transaction, after user and session locks. */
public interface SessionRevocationStore {
    int revoke(long sessionId,long userId,UUID installation,Instant revokedAt);
}
