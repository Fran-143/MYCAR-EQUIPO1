package backend.push.dao;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AlertaPushControlJdbcDao {
    private final JdbcTemplate jdbc;

    public AlertaPushControlJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Control(
            long idAlerta,
            String estadoCiclo,
            LocalDate fechaUltimoEnvioLima,
            String motivoUltimo,
            LocalDate silenciadoHastaLima) {}

    public Optional<Control> obtener(long idAlerta) {
        return jdbc.query("""
                SELECT id_alerta, estado_ciclo, fecha_ultimo_envio_lima, motivo_ultimo, silenciado_hasta_lima
                FROM mycar.alerta_push_control
                WHERE id_alerta = ?
                """, (rs, i) -> new Control(
                rs.getLong("id_alerta"),
                rs.getString("estado_ciclo"),
                rs.getObject("fecha_ultimo_envio_lima", LocalDate.class),
                rs.getString("motivo_ultimo"),
                rs.getObject("silenciado_hasta_lima", LocalDate.class)), idAlerta)
                .stream().findFirst();
    }

    public void upsertEnvio(
            long idAlerta, String estadoCiclo, LocalDate fechaEnvioLima, String motivo) {
        jdbc.update("""
                INSERT INTO mycar.alerta_push_control(
                    id_alerta, estado_ciclo, fecha_ultimo_envio_lima, motivo_ultimo, actualizado_utc)
                VALUES (?, ?, ?, ?, clock_timestamp())
                ON CONFLICT (id_alerta) DO UPDATE SET
                    estado_ciclo = EXCLUDED.estado_ciclo,
                    fecha_ultimo_envio_lima = EXCLUDED.fecha_ultimo_envio_lima,
                    motivo_ultimo = EXCLUDED.motivo_ultimo,
                    actualizado_utc = clock_timestamp()
                """, idAlerta, estadoCiclo, fechaEnvioLima, motivo);
    }

    /** Detiene ciclos futuros al pasar a REALIZADA/ANULADA (limpia estado_ciclo). */
    public void detener(long idAlerta) {
        jdbc.update("""
                INSERT INTO mycar.alerta_push_control(id_alerta, estado_ciclo, actualizado_utc)
                VALUES (?, NULL, clock_timestamp())
                ON CONFLICT (id_alerta) DO UPDATE SET
                    estado_ciclo = NULL,
                    actualizado_utc = clock_timestamp()
                """, idAlerta);
    }
}
