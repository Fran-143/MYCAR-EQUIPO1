package backend.cierresesion.persistence;

import backend.cierresesion.SessionRevocationStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
public class JdbcSessionRevocationStore implements SessionRevocationStore {
    private final JdbcTemplate jdbc;
    public JdbcSessionRevocationStore(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Override public int revoke(long sessionId,long userId,UUID installation,Instant revokedAt){
        return jdbc.update("""
                UPDATE mycar.sesion SET estado_sesion='REVOCADA',revocada_utc=?
                WHERE id_sesion=? AND id_usuario=? AND id_instalacion=?
                  AND estado_sesion='VIGENTE' AND revocada_utc IS NULL
                """,OffsetDateTime.ofInstant(revokedAt,ZoneOffset.UTC),sessionId,userId,installation);
    }
}
