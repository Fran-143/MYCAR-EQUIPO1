package backend.push.dao;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxPushJdbcDao {
    private final JdbcTemplate jdbc;

    public OutboxPushJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Idempotente: no duplica (usuario, alerta, categoria, destino). */
    public int encolarSiNuevo(
            long usuario, Long idAlerta, Long idNotificacion,
            String categoria, String titulo, String cuerpo, String destino) {
        return jdbc.update("""
                INSERT INTO mycar.outbox_push (
                    id_usuario, id_alerta, id_notificacion, categoria,
                    titulo, cuerpo, destino, estado, intentos, creado_utc, actualizado_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', 0, clock_timestamp(), clock_timestamp())
                ON CONFLICT (id_usuario, id_alerta, categoria, destino) DO NOTHING
                """, usuario, idAlerta, idNotificacion, categoria, titulo, cuerpo, destino);
    }

    public List<OutboxFila> reclamarPendientes(int limite) {
        return jdbc.query("""
                SELECT id_outbox, id_usuario, id_alerta, id_notificacion, categoria,
                       titulo, cuerpo, destino, intentos
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
                rs.getInt("intentos")), limite);
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
            int intentos) {
    }
}
