package backend.push.dao;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecordatorioPushJdbcDao {
    private final JdbcTemplate jdbc;

    public RecordatorioPushJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AlertaActiva(
            long idAlerta,
            long idUsuario,
            long idVehiculo,
            String estado,
            String placa,
            String etiquetaServicio) {}

    /** Alertas activas de mantenimiento elegibles para push (no PROGRAMADA). */
    public List<AlertaActiva> listarActivasPush() {
        return jdbc.query("""
                SELECT a.id_alerta, v.id_usuario, a.id_vehiculo, a.estado, v.placa,
                       COALESCE(NULLIF(btrim(a.servicio_otro), ''), tm.nombre) AS etiqueta
                FROM mycar.alerta a
                JOIN mycar.vehiculo v ON v.id_vehiculo = a.id_vehiculo
                JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                WHERE a.estado IN ('PROXIMA', 'PENDIENTE', 'VENCIDA')
                  AND v.estado_vehiculo = 'ACTIVO'
                ORDER BY v.id_usuario, a.estado DESC, a.id_alerta
                """, (rs, i) -> new AlertaActiva(
                rs.getLong("id_alerta"),
                rs.getLong("id_usuario"),
                rs.getLong("id_vehiculo"),
                rs.getString("estado"),
                rs.getString("placa"),
                rs.getString("etiqueta")));
    }

    public List<AlertaActiva> listarActivasVehiculo(long idVehiculo) {
        return jdbc.query("""
                SELECT a.id_alerta, v.id_usuario, a.id_vehiculo, a.estado, v.placa,
                       COALESCE(NULLIF(btrim(a.servicio_otro), ''), tm.nombre) AS etiqueta
                FROM mycar.alerta a
                JOIN mycar.vehiculo v ON v.id_vehiculo = a.id_vehiculo
                JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                WHERE a.id_vehiculo = ?
                  AND a.estado IN ('PROXIMA', 'PENDIENTE', 'VENCIDA', 'REALIZADA', 'ANULADA')
                ORDER BY a.id_alerta
                """, (rs, i) -> new AlertaActiva(
                rs.getLong("id_alerta"),
                rs.getLong("id_usuario"),
                rs.getLong("id_vehiculo"),
                rs.getString("estado"),
                rs.getString("placa"),
                rs.getString("etiqueta")), idVehiculo);
    }
}
