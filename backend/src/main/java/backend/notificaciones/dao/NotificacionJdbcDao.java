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
                SELECT a.id_alerta, a.id_vehiculo, COALESCE(v.id_usuario, a.id_usuario) AS id_usuario,
                       a.estado, a.categoria, v.placa,
                       CASE WHEN a.categoria = 'SOAT' THEN 'SOAT'
                            WHEN a.categoria = 'LICENCIA' THEN 'LICENCIA'
                            ELSE COALESCE(NULLIF(btrim(a.servicio_otro), ''), tm.nombre, 'Mantenimiento')
                       END AS etiqueta
                FROM mycar.alerta a
                LEFT JOIN mycar.vehiculo v ON v.id_vehiculo = a.id_vehiculo
                LEFT JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                WHERE a.id_alerta = ?
                """, (rs, n) -> new AlertaContexto(
                rs.getLong("id_alerta"),
                (Long) rs.getObject("id_vehiculo"),
                rs.getLong("id_usuario"),
                rs.getString("estado"),
                rs.getString("categoria"),
                rs.getString("placa"),
                rs.getString("etiqueta")), idAlerta).stream().findFirst();
    }

    public List<AlertaContexto> listarAlertasVehiculoParaAvisos(long vehiculo) {
        return jdbc.query("""
                SELECT a.id_alerta, a.id_vehiculo, v.id_usuario, a.estado, a.categoria, v.placa,
                       CASE WHEN a.categoria = 'SOAT' THEN 'SOAT'
                            ELSE COALESCE(NULLIF(btrim(a.servicio_otro), ''), tm.nombre, 'Mantenimiento')
                       END AS etiqueta
                FROM mycar.alerta a
                JOIN mycar.vehiculo v ON v.id_vehiculo = a.id_vehiculo
                LEFT JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                WHERE a.id_vehiculo = ?
                  AND a.estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA','REALIZADA','ANULADA')
                ORDER BY a.id_alerta
                """, (rs, n) -> new AlertaContexto(
                rs.getLong("id_alerta"),
                rs.getLong("id_vehiculo"),
                rs.getLong("id_usuario"),
                rs.getString("estado"),
                rs.getString("categoria"),
                rs.getString("placa"),
                rs.getString("etiqueta")), vehiculo);
    }

    /** Alertas LICENCIA del usuario para reconciliar avisos internos. */
    public List<AlertaContexto> listarAlertasLicenciaUsuarioParaAvisos(long usuario) {
        return jdbc.query("""
                SELECT a.id_alerta, a.id_vehiculo, a.id_usuario, a.estado, a.categoria,
                       CAST(NULL AS VARCHAR) AS placa,
                       'LICENCIA' AS etiqueta
                FROM mycar.alerta a
                WHERE a.id_usuario = ?
                  AND a.categoria = 'LICENCIA'
                  AND a.id_vehiculo IS NULL
                  AND a.estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA','REALIZADA','ANULADA')
                ORDER BY a.id_alerta
                """, (rs, n) -> new AlertaContexto(
                rs.getLong("id_alerta"),
                (Long) rs.getObject("id_vehiculo"),
                rs.getLong("id_usuario"),
                rs.getString("estado"),
                rs.getString("categoria"),
                rs.getString("placa"),
                rs.getString("etiqueta")), usuario);
    }

    /**
     * Upsert idempotente por (usuario, alerta). No duplica filas.
     * Si estado_gestion destino es PENDIENTE y ya existía, refresca emisión y textos.
     */
    public void upsertPorAlerta(
            long usuario, long idAlerta, Long idVehiculo,
            String categoria, String estadoGestion, String titulo, String cuerpo) {
        String cat = (categoria == null || categoria.isBlank()) ? "MANTENIMIENTO" : categoria;
        jdbc.update("""
                INSERT INTO mycar.notificacion(
                    id_usuario, categoria, id_alerta, id_vehiculo,
                    estado_gestion, leida, titulo, cuerpo, destino,
                    fecha_emision_utc, fecha_actualizacion_utc)
                VALUES (?, ?, ?, ?, ?, FALSE, ?, ?, 'DETALLE_ALERTA',
                        clock_timestamp(), clock_timestamp())
                ON CONFLICT (id_usuario, id_alerta) WHERE id_alerta IS NOT NULL
                DO UPDATE SET
                    categoria = EXCLUDED.categoria,
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
                """, usuario, cat, idAlerta, idVehiculo, estadoGestion, titulo, cuerpo);
    }

    public int marcarGestionSiActiva(long idAlerta, String estadoGestion) {
        // Al pasar a historial (ATENDIDA/CERRADA) se marca leída: no debe quedar badge fantasma.
        return jdbc.update("""
                UPDATE mycar.notificacion
                SET estado_gestion = ?,
                    leida = TRUE,
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
                    leida = TRUE,
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
                    leida = TRUE,
                    fecha_actualizacion_utc = clock_timestamp()
                WHERE n.id_usuario = ?
                  AND n.estado_gestion = 'PENDIENTE'
                  AND n.id_alerta IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM mycar.alerta a WHERE a.id_alerta = n.id_alerta)
                """, usuario)
                + jdbc.update("""
                UPDATE mycar.notificacion
                SET leida = TRUE,
                    fecha_actualizacion_utc = clock_timestamp()
                WHERE id_usuario = ?
                  AND leida = FALSE
                  AND estado_gestion IN ('ATENDIDA', 'CERRADA')
                """, usuario);
    }

    public int contarPendientes(long usuario) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.notificacion
                WHERE id_usuario = ? AND estado_gestion = 'PENDIENTE'
                """, Integer.class, usuario);
        return n == null ? 0 : n;
    }

    /**
     * No leídas = avisos aún por gestionar que el usuario no ha abierto.
     * Historial (ATENDIDA/CERRADA) no cuenta para campana ni contador.
     */
    public int contarNoLeidas(long usuario) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.notificacion
                WHERE id_usuario = ?
                  AND leida = FALSE
                  AND estado_gestion = 'PENDIENTE'
                """, Integer.class, usuario);
        return n == null ? 0 : n;
    }

    /** Leídas globales (cualquier gestión); independiente del filtro de pestaña. */
    public int contarLeidas(long usuario) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.notificacion
                WHERE id_usuario = ? AND leida = TRUE
                """, Integer.class, usuario);
        return n == null ? 0 : n;
    }

    public List<ContratoNotificaciones.NotificacionDto> listar(
            long usuario, String pestana, String categoria, String filtroLectura) {
        boolean pendientes = "PENDIENTES".equals(pestana);
        boolean todasCat = categoria == null || categoria.isBlank() || "TODAS".equals(categoria);
        boolean todasLect = filtroLectura == null || filtroLectura.isBlank() || "TODAS".equals(filtroLectura);
        // Valor de n.leida cuando el filtro no es TODAS: true=LEIDAS, false=NO_LEIDAS.
        boolean leidaFiltro = "LEIDAS".equals(filtroLectura);
        String sql = """
                SELECT n.id_notificacion, n.categoria, n.estado_gestion, n.leida, n.titulo, n.cuerpo, n.destino,
                       n.id_alerta, n.id_vehiculo,
                       to_char(n.fecha_emision_utc AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS emitida,
                       a.estado AS estado_alerta,
                       v.placa,
                       CASE WHEN v.id_vehiculo IS NOT NULL
                            THEN trim(both FROM coalesce(v.marca,'') || ' ' || coalesce(v.modelo,''))
                            ELSE NULL END AS vehiculo_etiqueta
                FROM mycar.notificacion n
                LEFT JOIN mycar.alerta a ON a.id_alerta = n.id_alerta
                LEFT JOIN mycar.vehiculo v ON v.id_vehiculo = n.id_vehiculo
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
                rs.getString("estado_alerta"),
                rs.getString("placa"),
                rs.getString("vehiculo_etiqueta")),
                usuario,
                todasCat, todasCat ? "" : categoria,
                todasLect, leidaFiltro);
    }

    public Optional<ContratoNotificaciones.NotificacionDto> obtenerPropia(long usuario, long idNotificacion) {
        return jdbc.query("""
                SELECT n.id_notificacion, n.categoria, n.estado_gestion, n.leida, n.titulo, n.cuerpo, n.destino,
                       n.id_alerta, n.id_vehiculo,
                       to_char(n.fecha_emision_utc AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS emitida,
                       a.estado AS estado_alerta,
                       v.placa,
                       CASE WHEN v.id_vehiculo IS NOT NULL
                            THEN trim(both FROM coalesce(v.marca,'') || ' ' || coalesce(v.modelo,''))
                            ELSE NULL END AS vehiculo_etiqueta
                FROM mycar.notificacion n
                LEFT JOIN mycar.alerta a ON a.id_alerta = n.id_alerta
                LEFT JOIN mycar.vehiculo v ON v.id_vehiculo = n.id_vehiculo
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
                rs.getString("estado_alerta"),
                rs.getString("placa"),
                rs.getString("vehiculo_etiqueta")), usuario, idNotificacion).stream().findFirst();
    }

    /** Fila cruda para armar detalle enriquecido (sin textos derivados). */
    public Optional<DetalleRaw> obtenerDetalleRaw(long usuario, long idNotificacion) {
        return jdbc.query("""
                SELECT n.id_notificacion, n.categoria, n.estado_gestion, n.leida, n.titulo, n.cuerpo, n.destino,
                       n.id_alerta, n.id_vehiculo,
                       to_char(n.fecha_emision_utc AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS emitida,
                       a.estado AS estado_alerta, a.criterio, a.fecha_objetivo, a.km_objetivo, a.km_base, a.intervalo_km,
                       CASE WHEN a.categoria = 'SOAT' THEN 'Vencimiento de SOAT'
                            WHEN a.categoria = 'LICENCIA' THEN 'Vencimiento de licencia'
                            ELSE COALESCE(NULLIF(btrim(a.servicio_otro), ''), tm.nombre, n.titulo)
                       END AS nombre_evento,
                       CASE WHEN v.id_vehiculo IS NOT NULL
                            THEN trim(both FROM coalesce(v.marca,'') || ' ' || coalesce(v.modelo,''))
                            ELSE NULL END AS vehiculo_etiqueta,
                       v.placa,
                       c.frecuencia AS frecuencia_km,
                       to_char(
                         (SELECT max(l.fecha_lectura_utc) FROM mycar.lectura_kilometraje l
                          WHERE l.id_vehiculo = n.id_vehiculo) AT TIME ZONE 'America/Lima',
                         'YYYY-MM-DD') AS ultima_km_lima,
                       to_char(c.proximo_envio_utc AT TIME ZONE 'America/Lima', 'YYYY-MM-DD') AS proximo_km_lima,
                       (SELECT l.valor_km FROM mycar.lectura_kilometraje l
                        WHERE l.id_vehiculo = n.id_vehiculo
                        ORDER BY l.fecha_lectura_utc DESC, l.id_lectura_km DESC LIMIT 1) AS km_actual
                FROM mycar.notificacion n
                LEFT JOIN mycar.alerta a ON a.id_alerta = n.id_alerta
                LEFT JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                LEFT JOIN mycar.vehiculo v ON v.id_vehiculo = n.id_vehiculo
                LEFT JOIN mycar.config_recordatorio_km c ON c.id_vehiculo = n.id_vehiculo
                WHERE n.id_usuario = ? AND n.id_notificacion = ?
                """, (rs, i) -> new DetalleRaw(
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
                rs.getString("estado_alerta"),
                rs.getString("criterio"),
                rs.getObject("fecha_objetivo", java.time.LocalDate.class),
                enteroNullable(rs.getObject("km_objetivo")),
                enteroNullable(rs.getObject("km_base")),
                enteroNullable(rs.getObject("intervalo_km")),
                rs.getString("nombre_evento"),
                rs.getString("vehiculo_etiqueta"),
                rs.getString("placa"),
                rs.getString("frecuencia_km"),
                rs.getString("ultima_km_lima"),
                rs.getString("proximo_km_lima"),
                enteroNullable(rs.getObject("km_actual"))), usuario, idNotificacion).stream().findFirst();
    }

    /** JDBC puede devolver NUMERIC como BigDecimal (p. ej. lectura_kilometraje.valor_km). */
    private static Integer enteroNullable(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Integer i) {
            return i;
        }
        if (valor instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalStateException("Valor numérico inesperado: " + valor.getClass().getName());
    }

    public record DetalleRaw(
            long idNotificacion,
            String categoria,
            String estadoGestion,
            boolean leida,
            String titulo,
            String cuerpo,
            String destino,
            Long idAlerta,
            Long idVehiculo,
            String fechaEmisionUtc,
            String estadoAlerta,
            String criterio,
            java.time.LocalDate fechaObjetivo,
            Integer kmObjetivo,
            Integer kmBase,
            Integer intervaloKm,
            String nombreEvento,
            String vehiculoEtiqueta,
            String placa,
            String frecuenciaKm,
            String ultimaActualizacionKm,
            String recordatorioCorrespondienteKm,
            Integer kmActual
    ) {}

    public int marcarLeida(long usuario, long idNotificacion) {
        return jdbc.update("""
                UPDATE mycar.notificacion
                SET leida = TRUE, fecha_actualizacion_utc = clock_timestamp()
                WHERE id_usuario = ? AND id_notificacion = ? AND leida = FALSE
                """, usuario, idNotificacion);
    }

    public record AlertaContexto(
            long idAlerta, Long idVehiculo, long idUsuario, String estado,
            String categoria, String placa, String etiquetaServicio
    ) { }
}
