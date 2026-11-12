package backend.push.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxPushJdbcDao {
    private final JdbcTemplate jdbc;

    public OutboxPushJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotente por {@code clave_idempotencia}.
     * @return id_outbox si insertó; empty si conflicto (reintento duplicado del mismo ciclo).
     */
    public Optional<Long> encolarSiNuevo(
            long usuario, Long idAlerta, Long idNotificacion,
            String categoria, String titulo, String cuerpo, String destino,
            String claveIdempotencia) {
        Long id = jdbc.query("""
                INSERT INTO mycar.outbox_push (
                    id_usuario, id_alerta, id_notificacion, categoria,
                    titulo, cuerpo, destino, estado, intentos,
                    clave_idempotencia, creado_utc, actualizado_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', 0, ?, clock_timestamp(), clock_timestamp())
                ON CONFLICT (clave_idempotencia) DO NOTHING
                RETURNING id_outbox
                """, rs -> rs.next() ? rs.getLong(1) : null,
                usuario, idAlerta, idNotificacion, categoria, titulo, cuerpo, destino, claveIdempotencia);
        return Optional.ofNullable(id);
    }

    public void vincularAlertas(long idOutbox, List<Long> idAlertas) {
        for (Long idAlerta : idAlertas) {
            if (idAlerta == null) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO mycar.outbox_push_alerta(id_outbox, id_alerta)
                    VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """, idOutbox, idAlerta);
        }
    }

    public List<Long> alertasDeOutbox(long idOutbox) {
        return jdbc.queryForList("""
                SELECT id_alerta FROM mycar.outbox_push_alerta WHERE id_outbox = ?
                ORDER BY id_alerta
                """, Long.class, idOutbox);
    }

    public List<OutboxFila> reclamarPendientes(int limite) {
        return jdbc.query("""
                SELECT id_outbox, id_usuario, id_alerta, id_notificacion, categoria,
                       titulo, cuerpo, destino, intentos, clave_idempotencia
                FROM mycar.outbox_push
                WHERE estado IN ('PENDIENTE', 'ERROR') AND intentos < 8
                ORDER BY creado_utc
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, (rs, i) -> new OutboxFila(
                rs.getLong("id_outbox"),
                rs.getLong("id_usuario"),
                (Long) rs.getObject("id_alerta"),
                (Long) rs.getObject("id_notificacion"),
                rs.getString("categoria"),
                rs.getString("titulo"),
                rs.getString("cuerpo"),
                rs.getString("destino"),
                rs.getInt("intentos"),
                rs.getString("clave_idempotencia")), limite);
    }

    public void marcar(long id, String estado, String error) {
        jdbc.update("""
                UPDATE mycar.outbox_push
                SET estado = ?,
                    intentos = intentos + 1,
                    ultimo_error = ?,
                    actualizado_utc = clock_timestamp()
                WHERE id_outbox = ?
                """, estado, error, id);
    }

    public record OutboxFila(
            long idOutbox,
            long idUsuario,
            Long idAlerta,
            Long idNotificacion,
            String categoria,
            String titulo,
            String cuerpo,
            String destino,
            int intentos,
            String claveIdempotencia) {
    }
}
