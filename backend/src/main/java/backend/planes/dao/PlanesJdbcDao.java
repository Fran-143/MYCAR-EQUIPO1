package backend.planes.dao;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PlanesJdbcDao {
    private final JdbcTemplate jdbc;

    public PlanesJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PlanRow(
            long idPlan,
            String codigo,
            String nombre,
            int maxVehiculos,
            BigDecimal precioDemo,
            String moneda,
            String periodicidad,
            String descripcionBeneficios,
            boolean contratacionSandbox,
            boolean activo
    ) { }

    public record SuscripcionRow(
            long idSuscripcion,
            long idUsuario,
            long idPlan,
            String planCodigo,
            String planNombre,
            String estado,
            String origen,
            String modalidad,
            Instant inicioUtc,
            Instant finUtc,
            Long idOperacionPago,
            int maxVehiculos
    ) { }

    public record SolicitudRow(
            long idSolicitud,
            String codigo,
            long idUsuario,
            long idPlan,
            String modalidad,
            String estado,
            Long idOperacionPago,
            Instant creadoUtc,
            Instant actualizadoUtc,
            Instant decididoUtc,
            Long idAdminDecision,
            String motivoRechazo,
            String observacionAdmin,
            Long idSuscripcionResultado,
            int versionFila,
            String referenciaOperacion,
            String proveedorOperacion,
            String estadoOperacion,
            BigDecimal importeOperacion,
            String monedaOperacion
    ) { }

    public record OperacionRow(
            long id,
            long idUsuario,
            long idPlan,
            String planCodigo,
            String referencia,
            String proveedor,
            String ambiente,
            BigDecimal importe,
            String moneda,
            String estado,
            String idExterno,
            String initPoint,
            String sandboxInitPoint,
            Instant creadoUtc,
            Instant verificadoUtc,
            String evidencia,
            int versionFila
    ) { }

    public record EventoRow(
            long idEvento,
            String tipoEvento,
            String detalle,
            Instant creadoUtc,
            String referenciaOperacion
    ) { }

    private static final RowMapper<PlanRow> PLAN_MAPPER = (rs, i) -> new PlanRow(
            rs.getLong("id_plan"),
            rs.getString("codigo"),
            rs.getString("nombre"),
            rs.getInt("max_vehiculos_registrados"),
            (BigDecimal) rs.getObject("precio_demo"),
            rs.getString("moneda"),
            rs.getString("periodicidad"),
            rs.getString("descripcion_beneficios"),
            rs.getBoolean("contratacion_sandbox_habilitada"),
            rs.getBoolean("activo")
    );

    private static Instant ts(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    public List<PlanRow> listarActivos() {
        return jdbc.query("""
                SELECT id_plan, codigo, nombre, max_vehiculos_registrados,
                       precio_demo, moneda, periodicidad, descripcion_beneficios,
                       contratacion_sandbox_habilitada, activo
                FROM mycar.plan
                WHERE activo
                ORDER BY CASE codigo WHEN 'FREE' THEN 0 WHEN 'PREMIUM' THEN 1 ELSE 2 END
                """, PLAN_MAPPER);
    }

    public Optional<PlanRow> porCodigo(String codigo) {
        var rows = jdbc.query("""
                SELECT id_plan, codigo, nombre, max_vehiculos_registrados,
                       precio_demo, moneda, periodicidad, descripcion_beneficios,
                       contratacion_sandbox_habilitada, activo
                FROM mycar.plan WHERE codigo = ? AND activo
                """, PLAN_MAPPER, codigo);
        return rows.stream().findFirst();
    }

    public Optional<SuscripcionRow> suscripcionActiva(long idUsuario) {
        var rows = jdbc.query("""
                SELECT s.id_suscripcion, s.id_usuario, s.id_plan, p.codigo AS plan_codigo,
                       p.nombre AS plan_nombre, s.estado, s.origen, s.modalidad,
                       s.inicio_utc, s.fin_utc, s.id_operacion_pago, p.max_vehiculos_registrados
                FROM mycar.suscripcion s
                JOIN mycar.plan p ON p.id_plan = s.id_plan
                WHERE s.id_usuario = ? AND s.estado = 'ACTIVA'
                ORDER BY s.inicio_utc DESC
                LIMIT 1
                """, (rs, i) -> mapSuscripcion(rs), idUsuario);
        return rows.stream().findFirst();
    }

    private SuscripcionRow mapSuscripcion(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SuscripcionRow(
                rs.getLong("id_suscripcion"),
                rs.getLong("id_usuario"),
                rs.getLong("id_plan"),
                rs.getString("plan_codigo"),
                rs.getString("plan_nombre"),
                rs.getString("estado"),
                rs.getString("origen"),
                rs.getString("modalidad"),
                ts(rs.getTimestamp("inicio_utc")),
                ts(rs.getTimestamp("fin_utc")),
                (Long) rs.getObject("id_operacion_pago"),
                rs.getInt("max_vehiculos_registrados")
        );
    }

    public int marcarVencida(long idSuscripcion) {
        return jdbc.update("""
                UPDATE mycar.suscripcion
                SET estado = 'VENCIDA',
                    fin_utc = COALESCE(fin_utc, clock_timestamp())
                WHERE id_suscripcion = ? AND estado = 'ACTIVA'
                """, idSuscripcion);
    }

    /** Vence todas las suscripciones Premium activas del usuario (cancelación admin / abuso). */
    public int marcarPremiumActivasVencidas(long idUsuario) {
        return jdbc.update("""
                UPDATE mycar.suscripcion s
                SET estado = 'VENCIDA',
                    fin_utc = COALESCE(s.fin_utc, clock_timestamp())
                FROM mycar.plan p
                WHERE s.id_plan = p.id_plan
                  AND s.id_usuario = ?
                  AND s.estado = 'ACTIVA'
                  AND p.codigo = 'PREMIUM'
                """, idUsuario);
    }

    public Optional<String> ultimoEventoTipoReciente(long idUsuario, String tipo) {
        var rows = jdbc.query("""
                SELECT detalle FROM mycar.suscripcion_evento
                WHERE id_usuario = ? AND tipo_evento = ?
                ORDER BY creado_utc DESC, id_evento DESC
                LIMIT 1
                """, (rs, i) -> rs.getString(1), idUsuario, tipo);
        return rows.stream().findFirst();
    }

    public boolean huboEventoTras(long idUsuario, String tipo, String tipoAnteriorReferencia) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.suscripcion_evento e
                WHERE e.id_usuario = ?
                  AND e.tipo_evento = ?
                  AND e.creado_utc >= COALESCE((
                      SELECT max(e2.creado_utc) FROM mycar.suscripcion_evento e2
                      WHERE e2.id_usuario = ? AND e2.tipo_evento = ?
                  ), '-infinity'::timestamptz)
                """, Long.class, idUsuario, tipo, idUsuario, tipoAnteriorReferencia);
        return n != null && n > 0;
    }

    public long insertarSuscripcion(
            long idUsuario, long idPlan, String origen, String modalidad,
            Instant finUtc, Long idOperacionPago) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.suscripcion(
                    id_usuario, id_plan, estado, origen, modalidad, fin_utc, id_operacion_pago)
                VALUES (?, ?, 'ACTIVA', ?, ?, ?, ?)
                RETURNING id_suscripcion
                """, Long.class, idUsuario, idPlan, origen, modalidad,
                finUtc == null ? null : Timestamp.from(finUtc),
                idOperacionPago);
    }

    public long contarVehiculosInactivos(long idUsuario) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.vehiculo
                WHERE id_usuario = ? AND estado_vehiculo = 'INACTIVO'
                """, Long.class, idUsuario);
        return n == null ? 0L : n;
    }

    public Optional<Long> idVehiculoPrincipal(long idUsuario) {
        var rows = jdbc.query("""
                SELECT id_vehiculo FROM mycar.vehiculo
                WHERE id_usuario = ? AND es_principal
                LIMIT 1
                """, (rs, i) -> rs.getLong(1), idUsuario);
        return rows.stream().findFirst();
    }

    public long contarVehiculos(long idUsuario) {
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.vehiculo WHERE id_usuario = ?", Long.class, idUsuario);
        return n == null ? 0L : n;
    }

    public long contarVehiculosActivos(long idUsuario) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.vehiculo
                WHERE id_usuario = ? AND estado_vehiculo = 'ACTIVO'
                """, Long.class, idUsuario);
        return n == null ? 0L : n;
    }

    public void reconciliarActivosFree(long idUsuario, int limiteActivos) {
        // Mantener el principal si está activo; si no, el más reciente ACTIVO; desactivar el resto.
        jdbc.update("""
                WITH ranking AS (
                    SELECT id_vehiculo,
                           ROW_NUMBER() OVER (
                               ORDER BY es_principal DESC, creado_utc DESC, id_vehiculo DESC
                           ) AS rn
                    FROM mycar.vehiculo
                    WHERE id_usuario = ? AND estado_vehiculo = 'ACTIVO'
                )
                UPDATE mycar.vehiculo v
                SET estado_vehiculo = 'INACTIVO',
                    es_principal = FALSE
                FROM ranking r
                WHERE v.id_vehiculo = r.id_vehiculo
                  AND r.rn > ?
                """, idUsuario, Math.max(1, limiteActivos));

        Long principales = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.vehiculo
                WHERE id_usuario = ? AND estado_vehiculo = 'ACTIVO' AND es_principal
                """, Long.class, idUsuario);
        if (principales != null && principales == 0) {
            jdbc.update("""
                    UPDATE mycar.vehiculo
                    SET es_principal = TRUE
                    WHERE id_vehiculo = (
                        SELECT id_vehiculo FROM mycar.vehiculo
                        WHERE id_usuario = ? AND estado_vehiculo = 'ACTIVO'
                        ORDER BY creado_utc DESC, id_vehiculo DESC
                        LIMIT 1
                    )
                    """, idUsuario);
        }
    }

    public long crearOperacion(
            long idUsuario,
            long idPlan,
            String referencia,
            String proveedor,
            BigDecimal importe,
            String moneda,
            String estado
    ) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.operacion_pago_prueba(
                    id_usuario, id_plan, referencia_unica, proveedor, ambiente,
                    importe, moneda, estado)
                VALUES (?, ?, ?, ?, 'TEST', ?, ?, ?)
                RETURNING id_operacion_pago
                """, Long.class, idUsuario, idPlan, referencia, proveedor, importe, moneda, estado);
    }

    public void actualizarInitPoints(long id, String initPoint, String sandboxInitPoint, String idExterno) {
        jdbc.update("""
                UPDATE mycar.operacion_pago_prueba
                SET init_point = ?, sandbox_init_point = ?, id_externo = ?,
                    version_fila = version_fila + 1
                WHERE id_operacion_pago = ?
                """, initPoint, sandboxInitPoint, idExterno, id);
    }

    public Optional<OperacionRow> operacionPorReferencia(String referencia) {
        return operacionPorReferenciaUsuario(referencia, null);
    }

    public Optional<OperacionRow> operacionPorReferenciaUsuario(String referencia, Long idUsuario) {
        String sql = """
                SELECT o.id_operacion_pago, o.id_usuario, o.id_plan, p.codigo AS plan_codigo,
                       o.referencia_unica, o.proveedor, o.ambiente, o.importe, o.moneda, o.estado,
                       o.id_externo, o.init_point, o.sandbox_init_point, o.creado_utc,
                       o.verificado_utc, o.evidencia_verificacion, o.version_fila
                FROM mycar.operacion_pago_prueba o
                JOIN mycar.plan p ON p.id_plan = o.id_plan
                WHERE o.referencia_unica = ?
                """ + (idUsuario == null ? "" : " AND o.id_usuario = ?");
        Object[] args = idUsuario == null
                ? new Object[]{referencia}
                : new Object[]{referencia, idUsuario};
        var rows = jdbc.query(sql, (rs, i) -> mapOperacion(rs), args);
        return rows.stream().findFirst();
    }

    private OperacionRow mapOperacion(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OperacionRow(
                rs.getLong("id_operacion_pago"),
                rs.getLong("id_usuario"),
                rs.getLong("id_plan"),
                rs.getString("plan_codigo"),
                rs.getString("referencia_unica"),
                rs.getString("proveedor"),
                rs.getString("ambiente"),
                (BigDecimal) rs.getObject("importe"),
                rs.getString("moneda"),
                rs.getString("estado"),
                rs.getString("id_externo"),
                rs.getString("init_point"),
                rs.getString("sandbox_init_point"),
                ts(rs.getTimestamp("creado_utc")),
                ts(rs.getTimestamp("verificado_utc")),
                rs.getString("evidencia_verificacion"),
                rs.getInt("version_fila")
        );
    }

    public int actualizarEstadoOperacion(
            long id, String estadoEsperado, String nuevoEstado, String evidencia) {
        return jdbc.update("""
                UPDATE mycar.operacion_pago_prueba
                SET estado = ?,
                    verificado_utc = clock_timestamp(),
                    evidencia_verificacion = ?,
                    version_fila = version_fila + 1
                WHERE id_operacion_pago = ? AND estado = ?
                """, nuevoEstado, evidencia, id, estadoEsperado);
    }

    public int forzarEstadoOperacion(long id, String nuevoEstado, String evidencia) {
        return jdbc.update("""
                UPDATE mycar.operacion_pago_prueba
                SET estado = ?,
                    verificado_utc = clock_timestamp(),
                    evidencia_verificacion = ?,
                    version_fila = version_fila + 1
                WHERE id_operacion_pago = ?
                  AND estado IN ('CREADA', 'PENDIENTE')
                """, nuevoEstado, evidencia, id);
    }

    public Optional<OperacionRow> ultimaOperacionUsuario(long idUsuario) {
        var rows = jdbc.query("""
                SELECT o.id_operacion_pago, o.id_usuario, o.id_plan, p.codigo AS plan_codigo,
                       o.referencia_unica, o.proveedor, o.ambiente, o.importe, o.moneda, o.estado,
                       o.id_externo, o.init_point, o.sandbox_init_point, o.creado_utc,
                       o.verificado_utc, o.evidencia_verificacion, o.version_fila
                FROM mycar.operacion_pago_prueba o
                JOIN mycar.plan p ON p.id_plan = o.id_plan
                WHERE o.id_usuario = ?
                ORDER BY o.creado_utc DESC
                LIMIT 1
                """, (rs, i) -> mapOperacion(rs), idUsuario);
        return rows.stream().findFirst();
    }

    public void registrarEvento(
            long idUsuario, Long idSuscripcion, Long idOperacion, String tipo, String detalle) {
        jdbc.update("""
                INSERT INTO mycar.suscripcion_evento(
                    id_usuario, id_suscripcion, id_operacion_pago, tipo_evento, detalle)
                VALUES (?, ?, ?, ?, ?)
                """, idUsuario, idSuscripcion, idOperacion, tipo, detalle);
    }

    public List<EventoRow> eventosUsuario(long idUsuario, int limit) {
        return jdbc.query("""
                SELECT e.id_evento, e.tipo_evento, e.detalle, e.creado_utc,
                       o.referencia_unica AS referencia
                FROM mycar.suscripcion_evento e
                LEFT JOIN mycar.operacion_pago_prueba o ON o.id_operacion_pago = e.id_operacion_pago
                WHERE e.id_usuario = ?
                ORDER BY e.creado_utc DESC
                LIMIT ?
                """, (rs, i) -> new EventoRow(
                rs.getLong("id_evento"),
                rs.getString("tipo_evento"),
                rs.getString("detalle"),
                ts(rs.getTimestamp("creado_utc")),
                rs.getString("referencia")
        ), idUsuario, Math.min(Math.max(limit, 1), 100));
    }

    public boolean cuentaActiva(long idUsuario) {
        var rows = jdbc.query("""
                SELECT 1 FROM mycar.usuario
                WHERE id_usuario = ? AND estado_cuenta = 'ACTIVA'
                """, (rs, i) -> 1, idUsuario);
        return !rows.isEmpty();
    }

    public Optional<Map<String, Object>> usuarioBasico(long idUsuario) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT id_usuario, correo,
                       trim(both FROM coalesce(nombre,'') || ' ' || coalesce(apellido,'')) AS nombre,
                       estado_cuenta
                FROM mycar.usuario WHERE id_usuario = ?
                """, (rs, i) -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("idUsuario", rs.getLong("id_usuario"));
            m.put("correo", rs.getString("correo"));
            m.put("nombre", rs.getString("nombre") == null ? "" : rs.getString("nombre").strip());
            m.put("estadoCuenta", rs.getString("estado_cuenta"));
            return m;
        }, idUsuario);
        return rows.stream().findFirst();
    }

    public long countFreeActivas() {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.suscripcion s
                JOIN mycar.plan p ON p.id_plan = s.id_plan
                WHERE s.estado = 'ACTIVA' AND p.codigo = 'FREE'
                  AND (s.fin_utc IS NULL OR s.fin_utc > clock_timestamp())
                """, Long.class);
        return n == null ? 0L : n;
    }

    public long countPremiumPruebaActivas() {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.suscripcion s
                JOIN mycar.plan p ON p.id_plan = s.id_plan
                WHERE s.estado = 'ACTIVA' AND p.codigo = 'PREMIUM'
                  AND s.modalidad = 'PRUEBA_GRATUITA'
                  AND (s.fin_utc IS NULL OR s.fin_utc > clock_timestamp())
                """, Long.class);
        return n == null ? 0L : n;
    }

    public long countPremiumSandboxActivas() {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.suscripcion s
                JOIN mycar.plan p ON p.id_plan = s.id_plan
                WHERE s.estado = 'ACTIVA' AND p.codigo = 'PREMIUM'
                  AND s.modalidad = 'PAGO_SANDBOX'
                  AND (s.fin_utc IS NULL OR s.fin_utc > clock_timestamp())
                """, Long.class);
        return n == null ? 0L : n;
    }

    public long countVencidas() {
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.suscripcion WHERE estado = 'VENCIDA'", Long.class);
        return n == null ? 0L : n;
    }

    public long countSolicitudesPorEstado(String estado) {
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.solicitud_suscripcion WHERE estado = ?",
                Long.class, estado);
        return n == null ? 0L : n;
    }

    public long countOperacionesPorEstados(List<String> estados) {
        if (estados == null || estados.isEmpty()) return 0L;
        String in = String.join(",", estados.stream().map(s -> "?").toList());
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.operacion_pago_prueba WHERE estado IN (" + in + ")",
                Long.class,
                estados.toArray());
        return n == null ? 0L : n;
    }

    /**
     * Cuenta cualquier solicitud de prueba gratuita registrada (cualquier estado).
     * Una sola por cuenta durante la vida del usuario (RF: prueba única).
     */
    public long countPruebasSolicitadasUsuario(long idUsuario) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.solicitud_suscripcion
                WHERE id_usuario = ? AND modalidad = 'PRUEBA_GRATUITA'
                """, Long.class, idUsuario);
        return n == null ? 0L : n;
    }

    /** @deprecated usar {@link #countPruebasSolicitadasUsuario(long)} */
    public long countPruebasAprobadasUsuario(long idUsuario) {
        return countPruebasSolicitadasUsuario(idUsuario);
    }

    public List<Map<String, Object>> listarAdmin(String filtro, String q, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id_usuario, u.correo,
                       trim(both FROM coalesce(u.nombre,'') || ' ' || coalesce(u.apellido,'')) AS nombre,
                       p.codigo AS plan_codigo, s.estado, s.origen, s.modalidad, s.inicio_utc, s.fin_utc
                FROM mycar.suscripcion s
                JOIN mycar.usuario u ON u.id_usuario = s.id_usuario
                JOIN mycar.plan p ON p.id_plan = s.id_plan
                WHERE s.estado = 'ACTIVA'
                  AND (s.fin_utc IS NULL OR s.fin_utc > clock_timestamp())
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if ("FREE".equalsIgnoreCase(filtro)) {
            sql.append(" AND p.codigo = 'FREE' ");
        } else if ("PREMIUM_PRUEBA".equalsIgnoreCase(filtro)) {
            sql.append(" AND p.codigo = 'PREMIUM' AND s.modalidad = 'PRUEBA_GRATUITA' ");
        } else if ("PREMIUM_SANDBOX".equalsIgnoreCase(filtro)) {
            sql.append(" AND p.codigo = 'PREMIUM' AND s.modalidad = 'PAGO_SANDBOX' ");
        } else if ("VENCIDAS".equalsIgnoreCase(filtro)) {
            sql.setLength(0);
            sql.append("""
                    SELECT u.id_usuario, u.correo,
                           trim(both FROM coalesce(u.nombre,'') || ' ' || coalesce(u.apellido,'')) AS nombre,
                           p.codigo AS plan_codigo, s.estado, s.origen, s.modalidad, s.inicio_utc, s.fin_utc
                    FROM mycar.suscripcion s
                    JOIN mycar.usuario u ON u.id_usuario = s.id_usuario
                    JOIN mycar.plan p ON p.id_plan = s.id_plan
                    WHERE s.estado = 'VENCIDA'
                    """);
        }
        if (q != null && !q.isBlank()) {
            sql.append("""
                     AND (lower(u.correo) LIKE ?
                          OR lower(coalesce(u.nombre,'') || ' ' || coalesce(u.apellido,'')) LIKE ?)
                    """);
            String like = "%" + q.strip().toLowerCase() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY s.inicio_utc DESC LIMIT ? ");
        args.add(Math.min(Math.max(limit, 1), 200));
        return jdbc.query(sql.toString(), (rs, i) -> {
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("idUsuario", rs.getLong("id_usuario"));
            m.put("correo", rs.getString("correo"));
            m.put("nombre", rs.getString("nombre"));
            m.put("planCodigo", rs.getString("plan_codigo"));
            m.put("estado", rs.getString("estado"));
            m.put("origen", rs.getString("origen"));
            m.put("modalidad", rs.getString("modalidad"));
            Timestamp ini = rs.getTimestamp("inicio_utc");
            Timestamp fin = rs.getTimestamp("fin_utc");
            m.put("inicioUtc", ini == null ? null : ini.toInstant().toString());
            m.put("finUtc", fin == null ? null : fin.toInstant().toString());
            String modalidad = rs.getString("modalidad");
            m.put("etiquetaSandbox",
                    "PAGO_SANDBOX".equals(modalidad) || "PRUEBA_GRATUITA".equals(modalidad));
            return m;
        }, args.toArray());
    }

    private SolicitudRow mapSolicitud(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SolicitudRow(
                rs.getLong("id_solicitud"),
                rs.getString("codigo"),
                rs.getLong("id_usuario"),
                rs.getLong("id_plan"),
                rs.getString("modalidad"),
                rs.getString("estado"),
                (Long) rs.getObject("id_operacion_pago"),
                ts(rs.getTimestamp("creado_utc")),
                ts(rs.getTimestamp("actualizado_utc")),
                ts(rs.getTimestamp("decidido_utc")),
                (Long) rs.getObject("id_admin_decision"),
                rs.getString("motivo_rechazo"),
                rs.getString("observacion_admin"),
                (Long) rs.getObject("id_suscripcion_resultado"),
                rs.getInt("version_fila"),
                rs.getString("referencia_unica"),
                rs.getString("proveedor"),
                rs.getString("estado_op"),
                (BigDecimal) rs.getObject("importe"),
                rs.getString("moneda")
        );
    }

    private static final String SOLICITUD_SELECT = """
            SELECT ss.id_solicitud, ss.codigo, ss.id_usuario, ss.id_plan, ss.modalidad, ss.estado,
                   ss.id_operacion_pago, ss.creado_utc, ss.actualizado_utc, ss.decidido_utc,
                   ss.id_admin_decision, ss.motivo_rechazo, ss.observacion_admin,
                   ss.id_suscripcion_resultado, ss.version_fila,
                   o.referencia_unica, o.proveedor, o.estado AS estado_op, o.importe, o.moneda
            FROM mycar.solicitud_suscripcion ss
            LEFT JOIN mycar.operacion_pago_prueba o ON o.id_operacion_pago = ss.id_operacion_pago
            """;

    public long crearSolicitud(
            String codigo, long idUsuario, long idPlan, String modalidad,
            String estado, Long idOperacionPago) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.solicitud_suscripcion(
                    codigo, id_usuario, id_plan, modalidad, estado, id_operacion_pago)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id_solicitud
                """, Long.class, codigo, idUsuario, idPlan, modalidad, estado, idOperacionPago);
    }

    public Optional<SolicitudRow> solicitudPorCodigo(String codigo) {
        var rows = jdbc.query(SOLICITUD_SELECT + " WHERE ss.codigo = ?",
                (rs, i) -> mapSolicitud(rs), codigo);
        return rows.stream().findFirst();
    }

    public Optional<SolicitudRow> solicitudPorCodigoForUpdate(String codigo) {
        var rows = jdbc.query(SOLICITUD_SELECT + " WHERE ss.codigo = ? FOR UPDATE OF ss",
                (rs, i) -> mapSolicitud(rs), codigo);
        return rows.stream().findFirst();
    }

    public Optional<SolicitudRow> solicitudAbiertaUsuario(long idUsuario) {
        var rows = jdbc.query(SOLICITUD_SELECT + """
                WHERE ss.id_usuario = ?
                  AND ss.estado IN ('PENDIENTE_PAGO','PENDIENTE_APROBACION')
                ORDER BY ss.creado_utc DESC LIMIT 1
                """, (rs, i) -> mapSolicitud(rs), idUsuario);
        return rows.stream().findFirst();
    }

    public Optional<SolicitudRow> solicitudPorOperacion(long idOperacion) {
        var rows = jdbc.query(SOLICITUD_SELECT + " WHERE ss.id_operacion_pago = ?",
                (rs, i) -> mapSolicitud(rs), idOperacion);
        return rows.stream().findFirst();
    }

    public List<SolicitudRow> solicitudesUsuario(long idUsuario, int limit) {
        return jdbc.query(SOLICITUD_SELECT + """
                WHERE ss.id_usuario = ?
                ORDER BY ss.creado_utc DESC LIMIT ?
                """, (rs, i) -> mapSolicitud(rs), idUsuario, Math.min(Math.max(limit, 1), 50));
    }

    public int actualizarEstadoSolicitud(
            long id, int versionEsperada, String estadoEsperado, String nuevoEstado) {
        return jdbc.update("""
                UPDATE mycar.solicitud_suscripcion
                SET estado = ?, actualizado_utc = clock_timestamp(), version_fila = version_fila + 1
                WHERE id_solicitud = ? AND version_fila = ? AND estado = ?
                """, nuevoEstado, id, versionEsperada, estadoEsperado);
    }

    public int decidirSolicitud(
            long id, int versionEsperada, String estadoEsperado, String nuevoEstado,
            Long idAdmin, String motivo, String observacion, Long idSuscripcion) {
        return jdbc.update("""
                UPDATE mycar.solicitud_suscripcion
                SET estado = ?,
                    decidido_utc = clock_timestamp(),
                    actualizado_utc = clock_timestamp(),
                    id_admin_decision = ?,
                    motivo_rechazo = ?,
                    observacion_admin = ?,
                    id_suscripcion_resultado = ?,
                    version_fila = version_fila + 1
                WHERE id_solicitud = ? AND version_fila = ? AND estado = ?
                """, nuevoEstado, idAdmin, motivo, observacion, idSuscripcion,
                id, versionEsperada, estadoEsperado);
    }

    public Optional<OperacionRow> operacionPorId(long id) {
        var rows = jdbc.query("""
                SELECT o.id_operacion_pago, o.id_usuario, o.id_plan, p.codigo AS plan_codigo,
                       o.referencia_unica, o.proveedor, o.ambiente, o.importe, o.moneda, o.estado,
                       o.id_externo, o.init_point, o.sandbox_init_point, o.creado_utc,
                       o.verificado_utc, o.evidencia_verificacion, o.version_fila
                FROM mycar.operacion_pago_prueba o
                JOIN mycar.plan p ON p.id_plan = o.id_plan
                WHERE o.id_operacion_pago = ?
                """, (rs, i) -> mapOperacion(rs), id);
        return rows.stream().findFirst();
    }

    /**
     * Listado principal admin: como máximo la solicitud más reciente por usuario
     * (creado_utc DESC, id_solicitud DESC). El historial completo se consulta aparte.
     */
    public List<Map<String, Object>> listarSolicitudesAdmin(String filtroEstado, String q, int limit) {
        StringBuilder inner = new StringBuilder("""
                SELECT DISTINCT ON (ss.id_usuario)
                       ss.codigo, ss.id_usuario,
                       u.correo,
                       trim(both FROM coalesce(u.nombre,'') || ' ' || coalesce(u.apellido,'')) AS nombre,
                       ss.modalidad, ss.estado, ss.creado_utc,
                       o.referencia_unica, o.proveedor, o.estado AS estado_op
                FROM mycar.solicitud_suscripcion ss
                JOIN mycar.usuario u ON u.id_usuario = ss.id_usuario
                LEFT JOIN mycar.operacion_pago_prueba o ON o.id_operacion_pago = ss.id_operacion_pago
                WHERE 1=1
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (filtroEstado != null && !filtroEstado.isBlank() && !"TODAS".equalsIgnoreCase(filtroEstado)) {
            if ("PENDIENTES".equalsIgnoreCase(filtroEstado)) {
                inner.append(" AND ss.estado IN ('PENDIENTE_PAGO','PENDIENTE_APROBACION') ");
            } else {
                inner.append(" AND ss.estado = ? ");
                args.add(filtroEstado.strip().toUpperCase());
            }
        }
        if (q != null && !q.isBlank()) {
            inner.append("""
                     AND (lower(u.correo) LIKE ?
                          OR lower(coalesce(u.nombre,'') || ' ' || coalesce(u.apellido,'')) LIKE ?
                          OR lower(ss.codigo) LIKE ?)
                    """);
            String like = "%" + q.strip().toLowerCase() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        inner.append(" ORDER BY ss.id_usuario, ss.creado_utc DESC, ss.id_solicitud DESC ");
        String sql = "SELECT * FROM (" + inner + ") recientes ORDER BY creado_utc DESC LIMIT ?";
        args.add(Math.min(Math.max(limit, 1), 200));
        return jdbc.query(sql, (rs, i) -> {
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("codigo", rs.getString("codigo"));
            m.put("idUsuario", rs.getLong("id_usuario"));
            m.put("correo", rs.getString("correo"));
            m.put("nombre", rs.getString("nombre"));
            m.put("modalidad", rs.getString("modalidad"));
            m.put("estado", rs.getString("estado"));
            Timestamp c = rs.getTimestamp("creado_utc");
            m.put("creadoUtc", c == null ? null : c.toInstant().toString());
            m.put("referenciaOperacion", rs.getString("referencia_unica"));
            m.put("proveedorOperacion", rs.getString("proveedor"));
            m.put("estadoOperacion", rs.getString("estado_op"));
            return m;
        }, args.toArray());
    }

    /** Solicitudes del mismo usuario distintas del código actual (historial administrativo). */
    public List<Map<String, Object>> solicitudesHermanasUsuario(long idUsuario, String codigoExcluir, int limit) {
        return jdbc.query("""
                SELECT ss.codigo, ss.modalidad, ss.estado, ss.creado_utc, ss.decidido_utc, ss.motivo_rechazo
                FROM mycar.solicitud_suscripcion ss
                WHERE ss.id_usuario = ?
                  AND ss.codigo <> ?
                ORDER BY ss.creado_utc DESC, ss.id_solicitud DESC
                LIMIT ?
                """, (rs, i) -> {
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("codigo", rs.getString("codigo"));
            m.put("modalidad", rs.getString("modalidad"));
            m.put("estado", rs.getString("estado"));
            Timestamp c = rs.getTimestamp("creado_utc");
            m.put("creadoUtc", c == null ? null : c.toInstant().toString());
            Timestamp d = rs.getTimestamp("decidido_utc");
            m.put("decididoUtc", d == null ? null : d.toInstant().toString());
            m.put("motivoRechazo", rs.getString("motivo_rechazo"));
            return m;
        }, idUsuario, codigoExcluir, Math.min(Math.max(limit, 1), 50));
    }

    public List<Long> usuariosPremiumVencidosPendientes() {
        return jdbc.query("""
                SELECT DISTINCT s.id_usuario
                FROM mycar.suscripcion s
                JOIN mycar.plan p ON p.id_plan = s.id_plan
                WHERE s.estado = 'ACTIVA'
                  AND p.codigo = 'PREMIUM'
                  AND s.fin_utc IS NOT NULL
                  AND s.fin_utc <= clock_timestamp()
                LIMIT 200
                """, (rs, i) -> rs.getLong(1));
    }

}
