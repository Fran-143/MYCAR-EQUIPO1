package backend.cuentas;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;

@Component
public class SesionRevocacion {
    private final JdbcTemplate jdbc;

    public SesionRevocacion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int revocarTodas(long userId, Instant now) {
        return jdbc.update("""
                UPDATE mycar.sesion
                   SET estado_sesion='REVOCADA', revocada_utc=?
                 WHERE id_usuario=? AND estado_sesion='VIGENTE'
                """, OffsetDateTime.ofInstant(now, ZoneOffset.UTC), userId);
    }

    public void desactivarPush(long userId) {
        try {
            jdbc.update("""
                    UPDATE mycar.dispositivo_push
                       SET activo=FALSE, actualizado_utc=clock_timestamp()
                     WHERE id_usuario=? AND activo=TRUE
                    """, userId);
        } catch (Exception ignored) {
            // tabla puede no existir en perfiles mínimos
        }
    }
}
