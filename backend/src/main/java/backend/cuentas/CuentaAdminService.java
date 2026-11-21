package backend.cuentas;

import backend.autenticacion.AuthenticationStore;
import backend.autenticacion.SessionAuthentication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CuentaAdminService {
    private static final Duration MFA_RECIENTE = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private final AuthenticationStore authStore;
    private final AuditoriaCuenta auditoria;
    private final SesionRevocacion sesiones;
    private final RestriccionRecuperacionService restriccion;

    public CuentaAdminService(JdbcTemplate jdbc, AuthenticationStore authStore, AuditoriaCuenta auditoria,
            SesionRevocacion sesiones, RestriccionRecuperacionService restriccion) {
        this.jdbc = jdbc;
        this.authStore = authStore;
        this.auditoria = auditoria;
        this.sesiones = sesiones;
        this.restriccion = restriccion;
    }

    public void exigirAdminMfa(SessionAuthentication.Principal principal) {
        if (principal == null || !principal.isAdmin()) throw CuentaException.forbidden("Se requiere administrador.");
        var session = authStore.findSession(principal.sessionId())
                .orElseThrow(CuentaException::mfaStepUp);
        Instant verificado = session.mfaVerificadoUtc();
        Instant now = authStore.now();
        if (verificado == null || verificado.isBefore(now.minus(MFA_RECIENTE))) {
            throw CuentaException.mfaStepUp();
        }
    }

    public Map<String, Object> listar(String q, String estado, String rol, int page, int size, boolean puedeVerAdmins) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        int offset = safePage * safeSize;
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (!puedeVerAdmins) {
            where.append(" AND u.codigo_rol='USUARIO' ");
        } else if (rol != null && !rol.isBlank() && !"TODOS".equalsIgnoreCase(rol)) {
            where.append(" AND u.codigo_rol=? ");
            args.add(rol.toUpperCase());
        }
        if (estado != null && !estado.isBlank() && !"TODOS".equalsIgnoreCase(estado)) {
            // Filtro por estado EFECTIVO visible en Administración (no solo estado_cuenta base).
            String e = estado.toUpperCase();
            switch (e) {
                case "ACTIVA" -> where.append("""
                         AND u.estado_cuenta='ACTIVA'
                         AND (u.login_bloqueo_hasta_utc IS NULL OR u.login_bloqueo_hasta_utc<=clock_timestamp())
                         AND NOT EXISTS (
                              SELECT 1 FROM mycar.restriccion_recuperacion r
                               WHERE r.id_usuario=u.id_usuario
                                 AND r.bloqueado_hasta_utc IS NOT NULL
                                 AND r.bloqueado_hasta_utc>clock_timestamp())
                        """);
                case "BLOQUEADA" -> where.append("""
                         AND (
                              u.estado_cuenta='BLOQUEADA'
                              OR (
                                   u.estado_cuenta='ACTIVA'
                                   AND (
                                        (u.login_bloqueo_hasta_utc IS NOT NULL
                                         AND u.login_bloqueo_hasta_utc>clock_timestamp())
                                        OR EXISTS (
                                             SELECT 1 FROM mycar.restriccion_recuperacion r
                                              WHERE r.id_usuario=u.id_usuario
                                                AND r.bloqueado_hasta_utc IS NOT NULL
                                                AND r.bloqueado_hasta_utc>clock_timestamp())
                                   )
                              )
                         )
                        """);
                case "SUSPENDIDA", "PENDIENTE_ELIMINACION", "ELIMINADA" -> {
                    where.append(" AND u.estado_cuenta=? ");
                    args.add(e);
                }
                default -> {
                    where.append(" AND u.estado_cuenta=? ");
                    args.add(e);
                }
            }
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (u.estado_cuenta<>'ELIMINADA') AND (lower(u.correo) LIKE ? OR lower(u.nombre||' '||u.apellido) LIKE ?) ");
            String like = "%" + q.strip().toLowerCase() + "%";
            args.add(like);
            args.add(like);
        }
        Integer total = jdbc.queryForObject("SELECT count(*) FROM mycar.usuario u " + where, Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(offset);
        List<Map<String, Object>> items = jdbc.query("""
                SELECT u.id_usuario, u.nombre, u.apellido, u.correo, u.estado_cuenta, u.codigo_rol,
                       u.bloqueo_admin_hasta_utc, u.login_bloqueo_hasta_utc, u.tipo_bloqueo,
                       u.constancia_eliminacion,
                       (SELECT r.bloqueado_hasta_utc FROM mycar.restriccion_recuperacion r
                         WHERE r.id_usuario=u.id_usuario) AS recup_hasta
                  FROM mycar.usuario u
                """ + where + " ORDER BY u.id_usuario DESC LIMIT ? OFFSET ?",
                (rs, n) -> mapResumen(rs), pageArgs.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("page", safePage);
        out.put("size", safeSize);
        out.put("total", total == null ? 0 : total);
        out.put("items", items);
        return out;
    }

    public Map<String, Object> detalle(long id) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT u.id_usuario, u.nombre, u.apellido, u.correo, u.estado_cuenta, u.codigo_rol,
                       u.fecha_registro_utc, u.ultimo_acceso_utc, u.motivo_estado, u.observacion_estado,
                       u.bloqueo_admin_hasta_utc, u.login_bloqueo_hasta_utc, u.tipo_bloqueo,
                       u.eliminacion_programada_utc, u.constancia_eliminacion
                  FROM mycar.usuario u WHERE u.id_usuario=?
                """, (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            String estado = rs.getString("estado_cuenta");
            if (CuentaEstados.ELIMINADA.equals(estado)) {
                m.put("idUsuario", rs.getLong("id_usuario"));
                m.put("estado", estado);
                m.put("constancia", rs.getString("constancia_eliminacion"));
                m.put("nombre", null);
                m.put("correo", null);
                m.put("anonima", true);
                return m;
            }
            m.put("idUsuario", rs.getLong("id_usuario"));
            m.put("nombreCompleto", rs.getString("nombre") + " " + rs.getString("apellido"));
            m.put("nombre", rs.getString("nombre"));
            m.put("apellido", rs.getString("apellido"));
            m.put("correo", rs.getString("correo"));
            m.put("rol", rs.getString("codigo_rol"));
            m.put("estado", estado);
            m.put("fechaRegistroUtc", rs.getTimestamp("fecha_registro_utc").toInstant().toString());
            m.put("ultimoAccesoUtc", rs.getTimestamp("ultimo_acceso_utc") == null ? null
                    : rs.getTimestamp("ultimo_acceso_utc").toInstant().toString());
            m.put("motivoEstado", rs.getString("motivo_estado"));
            m.put("observacionEstado", rs.getString("observacion_estado"));
            m.put("tipoBloqueo", rs.getString("tipo_bloqueo"));
            m.put("bloqueoAdminHastaUtc", ts(rs, "bloqueo_admin_hasta_utc"));
            m.put("loginBloqueoHastaUtc", ts(rs, "login_bloqueo_hasta_utc"));
            m.put("eliminacionProgramadaUtc", ts(rs, "eliminacion_programada_utc"));
            m.put("anonima", false);
            return m;
        }, id);
        if (rows.isEmpty()) throw CuentaException.notFound();
        Map<String, Object> det = rows.getFirst();
        if (Boolean.TRUE.equals(det.get("anonima"))) return det;

        restriccion.estado(id).ifPresent(r -> {
            // LinkedHashMap: Map.of no admite null (bloqueadoHastaUtc puede ser null).
            Map<String, Object> restr = new LinkedHashMap<>();
            restr.put("solicitudesCiclo", r.solicitudesCiclo());
            restr.put("nivelBloqueo", r.nivelBloqueo());
            restr.put("bloqueadoHastaUtc", r.bloqueadoHasta() == null ? null : r.bloqueadoHasta().toString());
            restr.put("bloqueado", r.bloqueado(Instant.now()));
            det.put("restriccionRecuperacion", restr);
            if (r.bloqueado(Instant.now()) && CuentaEstados.ACTIVA.equals(det.get("estado"))) {
                det.put("estadoVisible", CuentaEstados.BLOQUEADA);
                det.put("tipoBloqueoVisible", CuentaEstados.BLOQUEO_RECUPERACION);
                det.put("restriccion", "Solicitud de códigos");
            }
        });

        Integer vehiculos = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.vehiculo WHERE id_usuario=?", Integer.class, id);
        Integer activos = 0;
        try {
            activos = jdbc.queryForObject(
                    "SELECT count(*) FROM mycar.vehiculo WHERE id_usuario=? AND estado='ACTIVO'", Integer.class, id);
        } catch (Exception ignored) { }
        det.put("vehiculos", Map.of(
                "cantidad", vehiculos == null ? 0 : vehiculos,
                "activos", activos == null ? 0 : activos,
                "inactivos", Math.max(0, (vehiculos == null ? 0 : vehiculos) - (activos == null ? 0 : activos))));

        List<Map<String, Object>> sus = jdbc.query("""
                SELECT p.codigo, p.nombre, s.estado, s.inicio_utc, s.fin_utc
                  FROM mycar.suscripcion s JOIN mycar.plan p ON p.id_plan=s.id_plan
                 WHERE s.id_usuario=?
                 ORDER BY s.inicio_utc DESC LIMIT 1
                """, (rs, n) -> {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("plan", rs.getString("codigo"));
            s.put("nombrePlan", rs.getString("nombre"));
            s.put("estado", rs.getString("estado"));
            s.put("inicioUtc", rs.getTimestamp("inicio_utc").toInstant().toString());
            s.put("finUtc", rs.getTimestamp("fin_utc") == null ? null : rs.getTimestamp("fin_utc").toInstant().toString());
            return s;
        }, id);
        det.put("suscripcion", sus.isEmpty() ? null : sus.getFirst());

        List<Map<String, Object>> elim = jdbc.query("""
                SELECT id_solicitud, estado, solicitada_utc, vence_utc, motivo_codigo
                  FROM mycar.solicitud_eliminacion WHERE id_usuario=? AND estado='VIGENTE' LIMIT 1
                """, (rs, n) -> Map.of(
                "idSolicitud", rs.getLong(1),
                "estado", rs.getString(2),
                "solicitadaUtc", rs.getTimestamp(3).toInstant().toString(),
                "venceUtc", rs.getTimestamp(4).toInstant().toString(),
                "motivoCodigo", rs.getString(5) == null ? "" : rs.getString(5)
        ), id);
        det.put("solicitudEliminacion", elim.isEmpty() ? null : elim.getFirst());
        return det;
    }

    public List<Map<String, Object>> historial(long id) {
        return jdbc.query("""
                SELECT id_evento, accion, estado_anterior, estado_posterior, motivo, resultado, creado_utc, id_actor
                  FROM mycar.historial_estado_cuenta
                 WHERE id_usuario_afectado=? OR constancia_ref=(
                        SELECT constancia_eliminacion FROM mycar.usuario WHERE id_usuario=?)
                 ORDER BY creado_utc DESC LIMIT 100
                """, (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idEvento", rs.getLong(1));
            m.put("accion", rs.getString(2));
            m.put("estadoAnterior", rs.getString(3));
            m.put("estadoPosterior", rs.getString(4));
            m.put("motivo", rs.getString(5));
            m.put("resultado", rs.getString(6));
            m.put("creadoUtc", rs.getTimestamp(7).toInstant().toString());
            m.put("idActor", rs.getObject(8));
            return m;
        }, id, id);
    }

    @Transactional
    public Map<String, Object> suspender(long actorId, long targetId, String motivo, String comentario) {
        if (actorId == targetId) throw CuentaException.forbidden("No puedes suspender tu propia cuenta.");
        requireMotivo(motivo);
        var u = lockUser(targetId);
        if (CuentaEstados.ELIMINADA.equals(u.estado)) throw CuentaException.estadoInvalido("Cuenta eliminada.");
        if (CuentaEstados.SUSPENDIDA.equals(u.estado)) throw CuentaException.conflict("La cuenta ya está suspendida.");
        if ("ADMIN".equals(u.rol) && actorId != targetId) {
            // permitir solo si actor es admin (ya validado); bloquear auto-acciones ya cubierto
        }
        String anterior = u.estado;
        Instant now = authStore.now();
        jdbc.update("""
                UPDATE mycar.usuario
                   SET estado_cuenta='SUSPENDIDA', motivo_estado=?, observacion_estado=?,
                       tipo_bloqueo=NULL, bloqueo_admin_hasta_utc=NULL
                 WHERE id_usuario=?
                """, motivo, comentario, targetId);
        sesiones.revocarTodas(targetId, now);
        sesiones.desactivarPush(targetId);
        auditoria.registrar(targetId, null, actorId, "SUSPENDER", anterior, CuentaEstados.SUSPENDIDA, motivo, null, "OK");
        return Map.of("estado", CuentaEstados.SUSPENDIDA, "resultado", "OK");
    }

    @Transactional
    public Map<String, Object> levantarSuspension(long actorId, long targetId, String motivo) {
        if (actorId == targetId) throw CuentaException.forbidden("Operación no permitida sobre tu cuenta.");
        requireMotivo(motivo);
        var u = lockUser(targetId);
        if (!CuentaEstados.SUSPENDIDA.equals(u.estado)) {
            throw CuentaException.estadoInvalido("La cuenta no está suspendida.");
        }
        Instant now = authStore.now();
        // No restaurar ACTIVA si hay bloqueo admin vigente independiente (no debería coexistir).
        jdbc.update("""
                UPDATE mycar.usuario
                   SET estado_cuenta='ACTIVA', motivo_estado=?, observacion_estado=NULL
                 WHERE id_usuario=?
                """, motivo, targetId);
        auditoria.registrar(targetId, null, actorId, "LEVANTAR_SUSPENSION",
                CuentaEstados.SUSPENDIDA, CuentaEstados.ACTIVA, motivo, null, "OK");
        return Map.of("estado", CuentaEstados.ACTIVA, "resultado", "OK", "ahoraUtc", now.toString());
    }

    @Transactional
    public Map<String, Object> bloquear(long actorId, long targetId, String motivo, int duracionMinutos, String observacion) {
        if (actorId == targetId) throw CuentaException.forbidden("No puedes bloquear tu propia cuenta.");
        requireMotivo(motivo);
        if (duracionMinutos < 1 || duracionMinutos > 60 * 24 * 30) {
            throw CuentaException.validation(Map.of("duracionMinutos", "Indica una duración entre 1 minuto y 30 días."));
        }
        var u = lockUser(targetId);
        if (CuentaEstados.ELIMINADA.equals(u.estado) || CuentaEstados.PENDIENTE_ELIMINACION.equals(u.estado)) {
            throw CuentaException.estadoInvalido("No se puede bloquear en el estado actual.");
        }
        if (CuentaEstados.SUSPENDIDA.equals(u.estado)) {
            throw CuentaException.estadoInvalido("La cuenta está suspendida. Levanta la suspensión antes de un bloqueo temporal.");
        }
        Instant now = authStore.now();
        Instant hasta = now.plus(Duration.ofMinutes(duracionMinutos));
        jdbc.update("""
                UPDATE mycar.usuario
                   SET estado_cuenta='BLOQUEADA', tipo_bloqueo='ADMINISTRATIVA',
                       motivo_estado=?, observacion_estado=?, bloqueo_admin_hasta_utc=?
                 WHERE id_usuario=?
                """, motivo, observacion, utc(hasta), targetId);
        sesiones.revocarTodas(targetId, now);
        auditoria.registrar(targetId, null, actorId, "BLOQUEAR_ADMIN", u.estado, CuentaEstados.BLOQUEADA,
                motivo, "{\"hasta\":\"" + hasta + "\"}", "OK");
        return Map.of("estado", CuentaEstados.BLOQUEADA, "tipo", CuentaEstados.BLOQUEO_ADMIN,
                "desbloqueoUtc", hasta.toString(), "resultado", "OK");
    }

    @Transactional
    public Map<String, Object> desbloquear(long actorId, long targetId, String motivo) {
        if (actorId == targetId) throw CuentaException.forbidden("Operación no permitida sobre tu cuenta.");
        requireMotivo(motivo);
        var u = lockUser(targetId);
        if (!CuentaEstados.BLOQUEADA.equals(u.estado)
                && (u.loginBloqueo == null || !Instant.now().isBefore(u.loginBloqueo))) {
            // permitir limpiar solo login lock si estado ACTIVA
            if (CuentaEstados.ACTIVA.equals(u.estado)) {
                jdbc.update("UPDATE mycar.usuario SET login_bloqueo_hasta_utc=NULL, login_fallos_desde_utc=NULL WHERE id_usuario=?",
                        targetId);
                auditoria.registrar(targetId, null, actorId, "DESBLOQUEAR_LOGIN", u.estado, u.estado, motivo, null, "OK");
                return Map.of("estado", u.estado, "resultado", "OK");
            }
            throw CuentaException.estadoInvalido("La cuenta no está bloqueada.");
        }
        // No restaurar ACTIVA si hubiera suspensión — BLOQUEADA y SUSPENDIDA son excluyentes aquí.
        jdbc.update("""
                UPDATE mycar.usuario
                   SET estado_cuenta='ACTIVA', tipo_bloqueo=NULL, bloqueo_admin_hasta_utc=NULL,
                       login_bloqueo_hasta_utc=NULL, login_fallos_desde_utc=NULL,
                       motivo_estado=?, observacion_estado=NULL
                 WHERE id_usuario=?
                """, motivo, targetId);
        auditoria.registrar(targetId, null, actorId, "DESBLOQUEAR",
                CuentaEstados.BLOQUEADA, CuentaEstados.ACTIVA, motivo, null, "OK");
        return Map.of("estado", CuentaEstados.ACTIVA, "resultado", "OK");
    }

    public Map<String, Object> asistenciaRecuperacion(long targetId) {
        var det = detalle(targetId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("estado", det.get("estado"));
        out.put("solicitudEliminacion", det.get("solicitudEliminacion"));
        out.put("instrucciones", List.of(
                "Inicia la verificación del titular desde «Reactivar cuenta».",
                "El titular confirma el código en la app (sin compartir OTP ni contraseña contigo).",
                "Cuando figure verificado, confirma la reactivación con motivo obligatorio.",
                "La recuperación no reinicia el límite de una eliminación cada 30 días."));
        out.put("puedeReactivarAdmin", CuentaEstados.PENDIENTE_ELIMINACION.equals(det.get("estado")));
        return out;
    }

    private Map<String, Object> mapResumen(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        String estado = rs.getString("estado_cuenta");
        m.put("idUsuario", rs.getLong("id_usuario"));
        if (CuentaEstados.ELIMINADA.equals(estado)) {
            m.put("nombre", "Cuenta eliminada");
            m.put("correo", null);
            m.put("constancia", rs.getString("constancia_eliminacion"));
            m.put("anonima", true);
        } else {
            m.put("nombre", rs.getString("nombre") + " " + rs.getString("apellido"));
            m.put("correo", rs.getString("correo"));
            m.put("anonima", false);
        }
        m.put("estado", estado);
        m.put("rol", rs.getString("codigo_rol"));
        Instant adminHasta = rs.getTimestamp("bloqueo_admin_hasta_utc") == null ? null
                : rs.getTimestamp("bloqueo_admin_hasta_utc").toInstant();
        Instant loginHasta = rs.getTimestamp("login_bloqueo_hasta_utc") == null ? null
                : rs.getTimestamp("login_bloqueo_hasta_utc").toInstant();
        Instant recupHasta = rs.getTimestamp("recup_hasta") == null ? null
                : rs.getTimestamp("recup_hasta").toInstant();
        Instant now = Instant.now();
        // Prioridad: ELIMINADA/PENDIENTE/SUSPENDIDA > BLOQUEADA admin > login 10m > recuperación > ACTIVA.
        if (CuentaEstados.ELIMINADA.equals(estado)
                || CuentaEstados.PENDIENTE_ELIMINACION.equals(estado)
                || CuentaEstados.SUSPENDIDA.equals(estado)) {
            m.put("estadoVisible", estado);
        } else if (CuentaEstados.BLOQUEADA.equals(estado)) {
            m.put("estadoVisible", CuentaEstados.BLOQUEADA);
            m.put("tipoBloqueo", rs.getString("tipo_bloqueo") == null ? CuentaEstados.BLOQUEO_ADMIN : rs.getString("tipo_bloqueo"));
            Instant hasta = adminHasta != null ? adminHasta : loginHasta;
            if (hasta != null) m.put("desbloqueoUtc", hasta.toString());
        } else if (loginHasta != null && now.isBefore(loginHasta) && CuentaEstados.ACTIVA.equals(estado)) {
            m.put("estadoVisible", CuentaEstados.BLOQUEADA);
            m.put("tipoBloqueo", CuentaEstados.BLOQUEO_ACCESO);
            m.put("desbloqueoUtc", loginHasta.toString());
        } else if (recupHasta != null && now.isBefore(recupHasta) && CuentaEstados.ACTIVA.equals(estado)) {
            m.put("estadoVisible", CuentaEstados.BLOQUEADA);
            m.put("tipoBloqueo", CuentaEstados.BLOQUEO_RECUPERACION);
            m.put("desbloqueoUtc", recupHasta.toString());
        } else {
            m.put("estadoVisible", estado);
        }
        return m;
    }

    private record UserRow(long id, String estado, String rol, Instant loginBloqueo) { }

    private UserRow lockUser(long id) {
        List<UserRow> rows = jdbc.query("""
                SELECT id_usuario, estado_cuenta, codigo_rol, login_bloqueo_hasta_utc
                  FROM mycar.usuario WHERE id_usuario=? FOR NO KEY UPDATE
                """, (rs, n) -> new UserRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant()), id);
        if (rows.isEmpty()) throw CuentaException.notFound();
        return rows.getFirst();
    }

    private static void requireMotivo(String motivo) {
        if (motivo == null || motivo.strip().length() < 3) {
            throw CuentaException.validation(Map.of("motivo", "El motivo es obligatorio."));
        }
    }

    private static String ts(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        return rs.getTimestamp(col) == null ? null : rs.getTimestamp(col).toInstant().toString();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
