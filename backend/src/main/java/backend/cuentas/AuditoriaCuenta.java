package backend.cuentas;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class AuditoriaCuenta {
    private final JdbcTemplate jdbc;

    public AuditoriaCuenta(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(Long idUsuarioAfectado, String constanciaRef, Long idActor, String accion,
            String estadoAnterior, String estadoPosterior, String motivo, String detalleJson, String resultado) {
        jdbc.update("""
                INSERT INTO mycar.historial_estado_cuenta(
                    id_usuario_afectado, constancia_ref, id_actor, accion,
                    estado_anterior, estado_posterior, motivo, detalle_json, resultado, creado_utc)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                idUsuarioAfectado, constanciaRef, idActor, accion,
                estadoAnterior, estadoPosterior, motivo, detalleJson,
                resultado == null ? "OK" : resultado,
                OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
    }
}
