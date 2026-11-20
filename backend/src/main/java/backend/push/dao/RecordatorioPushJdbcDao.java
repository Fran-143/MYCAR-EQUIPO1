package backend.push.dao;

import java.time.LocalDate;
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
            Long idVehiculo,
            String categoria,
            String estado,
            String placa,
            String etiquetaServicio,
            LocalDate fechaObjetivo) {}

    public record NotifKmElegible(
            long idNotificacion,
            long idUsuario,
            long idVehiculo,
            String marca,
            String modelo,
            String placa,
            String titulo) {}

    /** Mantenimiento activo (no PROGRAMADA). */
    public List<AlertaActiva> listarMantenimientoActivasPush() {
        return jdbc.query("""
                SELECT a.id_alerta, v.id_usuario, a.id_vehiculo, a.categoria, a.estado, v.placa,
                       COALESCE(NULLIF(btrim(a.servicio_otro), ''), tm.nombre) AS etiqueta,
                       a.fecha_objetivo
                FROM mycar.alerta a
                JOIN mycar.vehiculo v ON v.id_vehiculo = a.id_vehiculo
                JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                WHERE a.categoria = 'MANTENIMIENTO'
                  AND a.estado IN ('PROXIMA', 'PENDIENTE', 'VENCIDA')
                  AND v.estado_vehiculo = 'ACTIVO'
                ORDER BY v.id_usuario, a.estado DESC, a.id_alerta
                """, this::mapAlerta);
    }

    /** SOAT activo por vehículo ACTIVO. */
    public List<AlertaActiva> listarSoatActivasPush() {
        return jdbc.query("""
                SELECT a.id_alerta, v.id_usuario, a.id_vehiculo, a.categoria, a.estado, v.placa,
                       'SOAT' AS etiqueta, a.fecha_objetivo
                FROM mycar.alerta a
                JOIN mycar.vehiculo v ON v.id_vehiculo = a.id_vehiculo
                WHERE a.categoria = 'SOAT'
                  AND a.estado IN ('PROXIMA', 'PENDIENTE', 'VENCIDA')
                  AND v.estado_vehiculo = 'ACTIVO'
                ORDER BY v.id_usuario, a.id_alerta
                """, this::mapAlerta);
    }

    public List<AlertaActiva> listarActivasVehiculo(long idVehiculo) {
        return jdbc.query("""
                SELECT a.id_alerta, v.id_usuario, a.id_vehiculo, a.categoria, a.estado, v.placa,
                       CASE WHEN a.categoria = 'SOAT' THEN 'SOAT'
                            ELSE COALESCE(NULLIF(btrim(a.servicio_otro), ''), tm.nombre)
                       END AS etiqueta,
                       a.fecha_objetivo
                FROM mycar.alerta a
                JOIN mycar.vehiculo v ON v.id_vehiculo = a.id_vehiculo
                LEFT JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                WHERE a.id_vehiculo = ?
                  AND a.estado IN ('PROXIMA', 'PENDIENTE', 'VENCIDA', 'REALIZADA', 'ANULADA')
                ORDER BY a.id_alerta
                """, this::mapAlerta, idVehiculo);
    }

    /** Compat: listado push histórico = mantenimiento. */
    public List<AlertaActiva> listarActivasPush() {
        return listarMantenimientoActivasPush();
    }

    /**
     * Avisos RF43 del día Lima aún pendientes de gestión, con interruptor global ACTIVO
     * y vehículo ACTIVO. No usa preferencia heredada kilometraje_on.
     */
    public List<NotifKmElegible> listarKilometrajeElegiblesHoy(LocalDate hoyLima) {
        return jdbc.query("""
                SELECT n.id_notificacion, n.id_usuario, n.id_vehiculo,
                       v.marca, v.modelo, v.placa, n.titulo
                FROM mycar.notificacion n
                JOIN mycar.vehiculo v ON v.id_vehiculo = n.id_vehiculo
                JOIN mycar.config_recordatorio_km_usuario u ON u.id_usuario = n.id_usuario
                WHERE n.categoria = 'KILOMETRAJE'
                  AND n.estado_gestion = 'PENDIENTE'
                  AND u.estado_recordatorios = 'ACTIVO'
                  AND v.estado_vehiculo = 'ACTIVO'
                  AND (n.fecha_emision_utc AT TIME ZONE 'America/Lima')::date = ?
                  AND NOT EXISTS (
                    SELECT 1 FROM mycar.outbox_push_notificacion opn
                    JOIN mycar.outbox_push op ON op.id_outbox = opn.id_outbox
                    WHERE opn.id_notificacion = n.id_notificacion
                      AND op.estado = 'ENVIADO'
                      AND op.clave_idempotencia LIKE 'KM:%:' || ?::text || '%'
                  )
                ORDER BY n.id_usuario, n.id_notificacion
                """, (rs, i) -> new NotifKmElegible(
                rs.getLong("id_notificacion"),
                rs.getLong("id_usuario"),
                rs.getLong("id_vehiculo"),
                rs.getString("marca"),
                rs.getString("modelo"),
                rs.getString("placa"),
                rs.getString("titulo")), hoyLima, hoyLima);
    }

    public boolean rf43GlobalActivo(long idUsuario) {
        Boolean on = jdbc.query("""
                SELECT estado_recordatorios = 'ACTIVO'
                FROM mycar.config_recordatorio_km_usuario WHERE id_usuario = ?
                """, rs -> rs.next() && rs.getBoolean(1), idUsuario);
        return Boolean.TRUE.equals(on);
    }

    private AlertaActiva mapAlerta(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        java.sql.Date fo = rs.getDate("fecha_objetivo");
        return new AlertaActiva(
                rs.getLong("id_alerta"),
                rs.getLong("id_usuario"),
                (Long) rs.getObject("id_vehiculo"),
                rs.getString("categoria"),
                rs.getString("estado"),
                rs.getString("placa"),
                rs.getString("etiqueta"),
                fo == null ? null : fo.toLocalDate());
    }
}
