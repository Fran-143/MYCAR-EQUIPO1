package backend.notificaciones.dao;

import backend.notificaciones.api.dto.ContratoNotificaciones;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificacionJdbcDao {
    private final JdbcTemplate jdbc;

    public NotificacionJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AlertaContexto> cargarAlerta(long idAlerta) {
        return jdbc.query("""
                SELECT a.id_alerta, a.id_vehiculo, v.id_usuario, a.estado, tm.nombre, v.placa,
                       COALESCE(NULLIF(btrim(a.servicio_otro), ''), tm.nombre) AS etiqueta
                FROM mycar.alerta a
                JOIN mycar.vehiculo v ON v.id_vehiculo = a.id_vehiculo
                JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                WHERE a.id_alerta = ?
                """, (rs, n) -> new AlertaContexto(
                rs.getLong("id_alerta"),
                rs.getLong("id_vehiculo"),
                rs.getLong("id_usuario"),
                rs.getString("estado"),
                rs.getString("nombre"),
                rs.getString("placa"),
                rs.getString("etiqueta")), idAlerta).stream().findFirst();
    }

    public List<AlertaContexto> listarAlertasVehiculoParaAvisos(long vehiculo) {
        return jdbc.query("""
                SELECT a.id_alerta, a.id_vehiculo, v.id_usuario, a.estado, tm.nombre, v.placa,
                       COALESCE(NULLIF(btrim(a.servicio_otro), ''), tm.nombre) AS etiqueta
                FROM mycar.alerta a
                JOIN mycar.vehiculo v ON v.id_vehiculo = a.id_vehiculo
                JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                WHERE a.id_vehiculo = ?
                  AND a.estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA','REALIZADA','ANULADA')
                ORDER BY a.id_alerta
                """, (rs, n) -> new AlertaContexto(
                rs.getLong("id_alerta"),
                rs.getLong("id_vehiculo"),
                rs.getLong("id_usuario"),
                rs.getString("estado"),
                rs.getString("nombre"),
                rs.getString("placa"),
                rs.getString("etiqueta")), vehiculo);
    }

    /**
     * Upsert idempotente por (usuario, alerta). No duplica filas.
     * Si estado_gestion destino es PENDIENTE y ya existía, refresca emisión y textos.
     */
    public void upsertPorAlerta(
            long usuario, long idAlerta, long idVehiculo,
            String estadoGestion, String titulo, String cuerpo) {
        jdbc.update("""
                INSERT INTO mycar.notificacion(
                    id_usuario, categoria, id_alerta, id_vehiculo,
                    estado_gestion, leida, titulo, cuerpo, destino,
                    fecha_emision_utc, fecha_actualizacion_utc)
                VALUES (?, 'MANTENIMIENTO', ?, ?, ?, FALSE, ?, ?, 'DETALLE_ALERTA',
                        clock_timestamp(), clock_timestamp())
                ON CONFLICT (id_usuario, id_alerta) WHERE id_alerta IS NOT NULL
                DO UPDATE SET
                    estado_gestion = EXCLUDED.estado_gestion,
                    titulo = EXCLUDED.titulo,
                    cuerpo = EXCLUDED.cuerpo,
                    id_vehiculo = EXCLUDED.id_vehiculo,
                    fecha_emision_utc = CASE
                        WHEN mycar.notificacion.estado_gestion = 'PENDIENTE'
                             AND EXCLUDED.estado_gestion = 'PENDIENTE'
                        THEN mycar.notificacion.fecha_emision_utc
                        WHEN EXCLUDED.estado_gestion = 'PENDIENTE'
                             AND mycar.notificacion.estado_gestion <> 'PENDIENTE'
                        THEN clock_timestamp()
                        ELSE mycar.notificacion.fecha_emision_utc
                    END,
                    fecha_actualizacion_utc = clock_timestamp()
                """, usuario, idAlerta, idVehiculo, estadoGestion, titulo, cuerpo);
    }

    public int marcarGestionSiActiva(long idAlerta, String estadoGestion) {
        return jdbc.update("""
                UPDATE mycar.notificacion
                SET estado_gestion = ?,
                    fecha_actualizacion_utc = clock_timestamp()
                WHERE id_alerta = ?
                  AND estado_gestion = 'PENDIENTE'
                """, estadoGestion, idAlerta);
    }

    public int desactivarPendientesDeAlerta(long idAlerta) {
        return marcarGestionSiActiva(idAlerta, "CERRADA");
    }

    /** Mueve a Historial avisos PENDIENTE cuya alerta ya no está vigente (REALIZADA/ANULADA/ausente). */
    public int conciliarVigencia(long usuario) {
        return jdbc.update("""
                UPDATE mycar.notificacion n
                SET estado_gestion = CASE
                        WHEN a.estado = 'REALIZADA' THEN 'ATENDIDA'
                        ELSE 'CERRADA'
                    END,
                    fecha_actualizacion_utc = clock_timestamp()
                FROM mycar.alerta a
                WHERE n.id_usuario = ?
                  AND n.id_alerta = a.id_alerta
                  AND n.estado_gestion = 'PENDIENTE'
                  AND a.estado IN ('REALIZADA', 'ANULADA')
                """, usuario)
                + jdbc.update("""
                UPDATE mycar.notificacion n
                SET estado_gestion = 'CERRADA',
                    fecha_actualizacion_utc = clock_timestamp()
                WHERE n.id_usuario = ?
                  AND n.estado_gestion = 'PENDIENTE'
                  AND n.id_alerta IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM mycar.alerta a WHERE a.id_alerta = n.id_alerta)
                """, usuario);
    }

    public int contarPendientes(long usuario) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.notificacion
                WHERE id_usuario = ? AND estado_gestion = 'PENDIENTE'
                """, Integer.class, usuario);
        return n == null ? 0 : n;
    }

    /** Contador global de no leídas del titular (independiente de pestaña/categoría/filtro UI). */
    public int contarNoLeidas(long usuario) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.notificacion
                WHERE id_usuario = ? AND leida = FALSE
                """, Integer.class, usuario);
        return n == null ? 0 : n;
    }

    public List<ContratoNotificaciones.NotificacionDto> listar(
            long usuario, String pestana, String categoria, String filtroLectura) {
        boolean pendientes = "PENDIENTES".equals(pestana);
        boolean todasCat = categoria == null || categoria.isBlank() || "TODAS".equals(categoria);
        boolean todasLect = filtroLectura == null || filtroLectura.isBlank() || "TODAS".equals(filtroLectura);
        Boolean soloLeidas = null;
        if ("NO_LEIDAS".equals(filtroLectura)) {
            soloLeidas = false;
        } else if ("LEIDAS".equals(filtroLectura)) {
            soloLeidas = true;
        }
        String sql = """
                SELECT n.id_notificacion, n.categoria, n.estado_gestion, n.leida, n.titulo, n.cuerpo, n.destino,
                       n.id_alerta, n.id_vehiculo,
                       to_char(n.fecha_emision_utc AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS emitida,
                       a.estado AS estado_alerta
                FROM mycar.notificacion n
                LEFT JOIN mycar.alerta a ON a.id_alerta = n.id_alerta
                WHERE n.id_usuario = ?
                  AND n.estado_gestion %s
                  AND (? OR n.categoria = ?)
                  AND (? OR n.leida = ?)
                ORDER BY n.fecha_emision_utc DESC, n.id_notificacion DESC
                """.formatted(pendientes ? "= 'PENDIENTE'" : "IN ('ATENDIDA','CERRADA')");
        return jdbc.query(sql, (rs, n) -> new ContratoNotificaciones.NotificacionDto(
                rs.getLong("id_notificacion"),
                rs.getString("categoria"),
                rs.getString("estado_gestion"),
                rs.getBoolean("leida"),
                rs.getString("titulo"),
                rs.getString("cuerpo"),
                rs.getString("destino"),
                (Long) rs.getObject("id_alerta"),
                (Long) rs.getObject("id_vehiculo"),
                rs.getString("emitida"),
                rs.getString("estado_alerta")),
                usuario,
                todasCat, todasCat ? "" : categoria,
                todasLect, Boolean.TRUE.equals(soloLeidas));
    }

    public Optional<ContratoNotificaciones.NotificacionDto> obtenerPropia(long usuario, long idNotificacion) {
        return jdbc.query("""
                SELECT n.id_notificacion, n.categoria, n.estado_gestion, n.leida, n.titulo, n.cuerpo, n.destino,
                       n.id_alerta, n.id_vehiculo,
                       to_char(n.fecha_emision_utc AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS emitida,
                       a.estado AS estado_alerta
                FROM mycar.notificacion n
                LEFT JOIN mycar.alerta a ON a.id_alerta = n.id_alerta
                WHERE n.id_usuario = ? AND n.id_notificacion = ?
                """, (rs, n) -> new ContratoNotificaciones.NotificacionDto(
                rs.getLong("id_notificacion"),
                rs.getString("categoria"),
                rs.getString("estado_gestion"),
                rs.getBoolean("leida"),
                rs.getString("titulo"),
                rs.getString("cuerpo"),
                rs.getString("destino"),
                (Long) rs.getObject("id_alerta"),
                (Long) rs.getObject("id_vehiculo"),
                rs.getString("emitida"),
                rs.getString("estado_alerta")), usuario, idNotificacion).stream().findFirst();
    }

    public int marcarLeida(long usuario, long idNotificacion) {
        return jdbc.update("""
                UPDATE mycar.notificacion
                SET leida = TRUE, fecha_actualizacion_utc = clock_timestamp()
                WHERE id_usuario = ? AND id_notificacion = ? AND leida = FALSE
                """, usuario, idNotificacion);
    }

    public record AlertaContexto(
            long idAlerta, long idVehiculo, long idUsuario, String estado,
            String nombreTipo, String placa, String etiquetaServicio
    ) { }
}
