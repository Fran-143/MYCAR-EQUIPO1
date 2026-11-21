package backend.cuentas;

import backend.localtest.SimulatedMailInbox;
import backend.registro.verificacion.MailGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * RF51 asistencia administrativa: el admin inicia verificación; el titular confirma OTP;
 * solo entonces el admin puede reactivar con motivo (sin ver OTP ni contraseña).
 */
@Service
public class ReactivacionAsistidaService {
    private static final Logger log = LoggerFactory.getLogger(ReactivacionAsistidaService.class);
    private static final Duration TTL_VERIFICACION = Duration.ofHours(2);

    private final JdbcTemplate jdbc;
    private final CuentaOtpService otp;
    private final AuditoriaCuenta auditoria;
    private final SesionRevocacion sesiones;
    private final MailGateway mail;
    private final ObjectProvider<SimulatedMailInbox> inbox;
    private final boolean desarrollo;

    public ReactivacionAsistidaService(
            JdbcTemplate jdbc,
            CuentaOtpService otp,
            AuditoriaCuenta auditoria,
            SesionRevocacion sesiones,
            MailGateway mail,
            ObjectProvider<SimulatedMailInbox> inbox,
            @Value("${mycar.recuperacion.desarrollo:false}") boolean desarrollo) {
        this.jdbc = jdbc;
        this.otp = otp;
        this.auditoria = auditoria;
        this.sesiones = sesiones;
        this.mail = mail;
        this.inbox = inbox;
        this.desarrollo = desarrollo;
    }

    public Map<String, Object> estadoParaAdmin(long targetId, Instant now) {
        Map<String, Object> out = new LinkedHashMap<>();
        var user = jdbc.query("""
                SELECT correo, estado_cuenta, eliminacion_programada_utc
                  FROM mycar.usuario WHERE id_usuario=?
                """, (r, n) -> new Object[]{r.getString(1), r.getString(2),
                r.getTimestamp(3) == null ? null : r.getTimestamp(3).toInstant()}, targetId)
                .stream().findFirst().orElseThrow(CuentaException::notFound);
        String estado = (String) user[1];
        out.put("idUsuario", targetId);
        out.put("correo", user[0]);
        out.put("estado", estado);
        var sol = jdbc.query("""
                SELECT id_solicitud, solicitada_utc, vence_utc, estado
                  FROM mycar.solicitud_eliminacion
                 WHERE id_usuario=? AND estado='VIGENTE'
                 ORDER BY solicitada_utc DESC LIMIT 1
                """, (r, n) -> Map.of(
                "idSolicitud", r.getLong(1),
                "solicitadaUtc", r.getTimestamp(2).toInstant().toString(),
                "venceUtc", r.getTimestamp(3).toInstant().toString(),
                "estado", r.getString(4)
        ), targetId).stream().findFirst().orElse(null);
        out.put("solicitudEliminacion", sol);
        if (sol != null) {
            Instant vence = Instant.parse((String) sol.get("venceUtc"));
            long dias = Math.max(0, ChronoUnit.DAYS.between(now, vence));
            out.put("diasRestantes", dias);
            out.put("plazoVigente", now.isBefore(vence));
        } else {
            out.put("diasRestantes", 0);
            out.put("plazoVigente", false);
        }
        if (!CuentaEstados.PENDIENTE_ELIMINACION.equals(estado)) {
            out.put("verificacion", null);
            out.put("titularVerificado", false);
            out.put("puedeReactivar", false);
            out.put("fase", "REACTIVADA");
            return out;
        }
        var ver = jdbc.query("""
                SELECT id_verificacion, estado, expira_utc, confirmada_utc
                  FROM mycar.verificacion_reactivacion_asistida
                 WHERE id_usuario=? AND estado IN ('PENDIENTE','CONFIRMADA')
                 ORDER BY creado_utc DESC LIMIT 1
                """, (r, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idVerificacion", r.getLong(1));
            m.put("estado", r.getString(2));
            m.put("expiraUtc", r.getTimestamp(3).toInstant().toString());
            if (r.getTimestamp(4) != null) m.put("confirmadaUtc", r.getTimestamp(4).toInstant().toString());
            return m;
        }, targetId).stream().findFirst().orElse(null);
        boolean expirada = false;
        if (ver != null) {
            Instant expira = Instant.parse((String) ver.get("expiraUtc"));
            if ("PENDIENTE".equals(ver.get("estado")) && !now.isBefore(expira)) {
                jdbc.update("""
                        UPDATE mycar.verificacion_reactivacion_asistida
                           SET estado='EXPIRADA' WHERE id_verificacion=?
                        """, ver.get("idVerificacion"));
                ver = null;
                expirada = true;
            }
        }
        if (ver == null && !expirada) {
            expirada = jdbc.query("""
                    SELECT 1 FROM mycar.verificacion_reactivacion_asistida
                     WHERE id_usuario=? AND estado='EXPIRADA'
                     ORDER BY creado_utc DESC LIMIT 1
                    """, (r, n) -> 1, targetId).stream().findFirst().isPresent();
        }
        out.put("verificacion", ver);
        boolean titularOk = ver != null && "CONFIRMADA".equals(ver.get("estado"));
        out.put("titularVerificado", titularOk);
        out.put("puedeReactivar", titularOk
                && CuentaEstados.PENDIENTE_ELIMINACION.equals(estado)
                && Boolean.TRUE.equals(out.get("plazoVigente")));
        String fase;
        if (titularOk) {
            fase = "TITULAR_VERIFICADO";
        } else if (ver != null && "PENDIENTE".equals(ver.get("estado"))) {
            fase = "PENDIENTE_VERIFICACION";
        } else if (expirada) {
            fase = "EXPIRADA";
        } else {
            fase = "NO_INICIADA";
        }
        out.put("fase", fase);
        return out;
    }

    @Transactional
    public Map<String, Object> iniciarVerificacion(long adminId, long targetId, Instant now) {
        var u = jdbc.query("""
                SELECT correo, estado_cuenta FROM mycar.usuario WHERE id_usuario=? FOR NO KEY UPDATE
                """, (r, n) -> new Object[]{r.getString(1), r.getString(2)}, targetId)
                .stream().findFirst().orElseThrow(CuentaException::notFound);
        if (!CuentaEstados.PENDIENTE_ELIMINACION.equals(u[1])) {
            throw CuentaException.estadoInvalido("Solo cuentas pendientes de eliminación admiten reactivación asistida.");
        }
        var sol = jdbc.query("""
                SELECT id_solicitud, vence_utc FROM mycar.solicitud_eliminacion
                 WHERE id_usuario=? AND estado='VIGENTE' FOR UPDATE
                """, (r, n) -> new Object[]{r.getLong(1), r.getTimestamp(2).toInstant()}, targetId)
                .stream().findFirst()
                .orElseThrow(() -> CuentaException.estadoInvalido("No hay solicitud vigente."));
        Instant vence = (Instant) sol[1];
        if (!now.isBefore(vence)) {
            throw CuentaException.estadoInvalido("El plazo de 30 días ha vencido. No es posible recuperar la cuenta.");
        }
        // Continuidad: si ya hay verificación PENDIENTE vigente, no emitir otro OTP.
        var vigente = jdbc.query("""
                SELECT id_verificacion, expira_utc FROM mycar.verificacion_reactivacion_asistida
                 WHERE id_usuario=? AND estado='PENDIENTE' FOR UPDATE
                """, (r, n) -> new Object[]{r.getLong(1), r.getTimestamp(2).toInstant()}, targetId)
                .stream().findFirst().orElse(null);
        if (vigente != null) {
            Instant expiraVigente = (Instant) vigente[1];
            if (now.isBefore(expiraVigente)) {
                Map<String, Object> keep = new LinkedHashMap<>();
                keep.put("resultado", "VERIFICACION_VIGENTE");
                keep.put("idVerificacion", vigente[0]);
                keep.put("expiraUtc", expiraVigente.toString());
                keep.put("titularVerificado", false);
                keep.put("fase", "PENDIENTE_VERIFICACION");
                keep.put("reiniciada", false);
                return keep;
            }
            jdbc.update("""
                    UPDATE mycar.verificacion_reactivacion_asistida
                       SET estado='EXPIRADA' WHERE id_verificacion=?
                    """, vigente[0]);
        }
        // Solo al iniciar/reenviar: cancela CONFIRMADA previa (si hubiera) y emite OTP nuevo.
        jdbc.update("""
                UPDATE mycar.verificacion_reactivacion_asistida
                   SET estado='CANCELADA'
                 WHERE id_usuario=? AND estado IN ('PENDIENTE','CONFIRMADA')
                """, targetId);
        Instant expira = now.plus(TTL_VERIFICACION);
        Long idVer = jdbc.queryForObject("""
                INSERT INTO mycar.verificacion_reactivacion_asistida(
                    id_usuario, id_admin, id_solicitud, estado, expira_utc, creado_utc)
                VALUES (?,?,?,'PENDIENTE',?,?) RETURNING id_verificacion
                """, Long.class, targetId, adminId, sol[0], utc(expira), utc(now));
        String correo = (String) u[0];
        var emit = otp.emitir(targetId, correo, CuentaEstados.FINALIDAD_VERIFICAR_REACTIVACION_ADMIN, now);
        auditoria.registrar(targetId, null, adminId, "INICIAR_VERIFICACION_REACTIVACION_ASISTIDA",
                CuentaEstados.PENDIENTE_ELIMINACION, CuentaEstados.PENDIENTE_ELIMINACION,
                "ASISTENCIA_SOPORTE", null, "OK");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultado", "VERIFICACION_INICIADA");
        out.put("idVerificacion", idVer);
        out.put("expiraUtc", expira.toString());
        out.put("titularVerificado", false);
        out.put("fase", "PENDIENTE_VERIFICACION");
        out.put("reiniciada", true);
        // Nunca devolver OTP al administrador (ni en local-test vía este endpoint).
        if (desarrollo && emit.codigoDesarrollo() != null) {
            log.info("otp_desarrollo finalidad={} (no expuesto al admin)", CuentaEstados.FINALIDAD_VERIFICAR_REACTIVACION_ADMIN);
        }
        return out;
    }

    /**
     * Verificación del código OTP desde Administración (mismo efecto que confirmarTitular).
     * No expone el OTP; solo consume el código introducido por el admin tras consulta local.
     */
    @Transactional
    public Map<String, Object> verificarCodigoAdmin(long adminId, long targetId, String codigo, Instant now) {
        var u = jdbc.query("""
                SELECT correo, estado_cuenta FROM mycar.usuario WHERE id_usuario=? FOR NO KEY UPDATE
                """, (r, n) -> new Object[]{r.getString(1), r.getString(2)}, targetId)
                .stream().findFirst().orElseThrow(CuentaException::notFound);
        if (!CuentaEstados.PENDIENTE_ELIMINACION.equals(u[1])) {
            throw CuentaException.estadoInvalido("La cuenta no está pendiente de eliminación.");
        }
        var ver = jdbc.query("""
                SELECT id_verificacion, expira_utc FROM mycar.verificacion_reactivacion_asistida
                 WHERE id_usuario=? AND estado='PENDIENTE' FOR UPDATE
                """, (r, n) -> new Object[]{r.getLong(1), r.getTimestamp(2).toInstant()}, targetId)
                .stream().findFirst()
                .orElseThrow(() -> CuentaException.validation(Map.of("codigo", "No hay verificación pendiente.")));
        Instant expira = (Instant) ver[1];
        if (!now.isBefore(expira)) {
            jdbc.update("UPDATE mycar.verificacion_reactivacion_asistida SET estado='EXPIRADA' WHERE id_verificacion=?", ver[0]);
            throw CuentaException.validation(Map.of("codigo", "La verificación ha expirado."));
        }
        otp.consumir(targetId, CuentaEstados.FINALIDAD_VERIFICAR_REACTIVACION_ADMIN, codigo, now);
        jdbc.update("""
                UPDATE mycar.verificacion_reactivacion_asistida
                   SET estado='CONFIRMADA', confirmada_utc=?
                 WHERE id_verificacion=?
                """, utc(now), ver[0]);
        auditoria.registrar(targetId, null, adminId, "VERIFICAR_CODIGO_REACTIVACION_ASISTIDA",
                CuentaEstados.PENDIENTE_ELIMINACION, CuentaEstados.PENDIENTE_ELIMINACION,
                "ADMIN_CON_CODIGO_TITULAR", null, "OK");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultado", "TITULAR_VERIFICADO");
        out.put("fase", "TITULAR_VERIFICADO");
        out.put("mensaje", "Identidad del titular verificada");
        out.put("titularVerificado", true);
        return out;
    }

    @Transactional
    public Map<String, Object> confirmarTitular(String correoRaw, String codigo, Instant now) {
        String correo = normalize(correoRaw);
        if (correo == null) throw CuentaException.validation(Map.of("correo", "Ingresa un correo válido."));
        var rows = jdbc.query("""
                SELECT id_usuario, estado_cuenta FROM mycar.usuario WHERE lower(btrim(correo))=?
                """, (r, n) -> new Object[]{r.getLong(1), r.getString(2)}, correo);
        // Anti-enumeración
        if (rows.isEmpty() || !CuentaEstados.PENDIENTE_ELIMINACION.equals(rows.getFirst()[1])) {
            throw CuentaException.validation(Map.of("credenciales", "No se pudo confirmar la verificación."));
        }
        long userId = (Long) rows.getFirst()[0];
        var ver = jdbc.query("""
                SELECT id_verificacion, expira_utc FROM mycar.verificacion_reactivacion_asistida
                 WHERE id_usuario=? AND estado='PENDIENTE' FOR UPDATE
                """, (r, n) -> new Object[]{r.getLong(1), r.getTimestamp(2).toInstant()}, userId)
                .stream().findFirst()
                .orElseThrow(() -> CuentaException.validation(Map.of("credenciales", "No se pudo confirmar la verificación.")));
        Instant expira = (Instant) ver[1];
        if (!now.isBefore(expira)) {
            jdbc.update("UPDATE mycar.verificacion_reactivacion_asistida SET estado='EXPIRADA' WHERE id_verificacion=?", ver[0]);
            throw CuentaException.validation(Map.of("codigo", "La verificación ha expirado."));
        }
        otp.consumir(userId, CuentaEstados.FINALIDAD_VERIFICAR_REACTIVACION_ADMIN, codigo, now);
        jdbc.update("""
                UPDATE mycar.verificacion_reactivacion_asistida
                   SET estado='CONFIRMADA', confirmada_utc=?
                 WHERE id_verificacion=?
                """, utc(now), ver[0]);
        auditoria.registrar(userId, null, userId, "CONFIRMAR_TITULAR_REACTIVACION_ASISTIDA",
                CuentaEstados.PENDIENTE_ELIMINACION, CuentaEstados.PENDIENTE_ELIMINACION,
                "TITULAR", null, "OK");
        return Map.of("resultado", "TITULAR_VERIFICADO",
                "mensaje", "Identidad confirmada. Soporte podrá completar la reactivación.");
    }

    @Transactional
    public Map<String, Object> reactivarAdmin(long adminId, long targetId, String motivo, String observacion, Instant now) {
        if (motivo == null || motivo.strip().length() < 3) {
            throw CuentaException.validation(Map.of("motivo", "El motivo administrativo es obligatorio."));
        }
        var u = jdbc.query("""
                SELECT correo, estado_cuenta FROM mycar.usuario WHERE id_usuario=? FOR NO KEY UPDATE
                """, (r, n) -> new Object[]{r.getString(1), r.getString(2)}, targetId)
                .stream().findFirst().orElseThrow(CuentaException::notFound);
        if (CuentaEstados.ELIMINADA.equals(u[1])) {
            throw CuentaException.estadoInvalido("No se puede reactivar una cuenta eliminada.");
        }
        if (!CuentaEstados.PENDIENTE_ELIMINACION.equals(u[1])) {
            throw CuentaException.estadoInvalido("La cuenta no está pendiente de eliminación.");
        }
        var ver = jdbc.query("""
                SELECT id_verificacion FROM mycar.verificacion_reactivacion_asistida
                 WHERE id_usuario=? AND estado='CONFIRMADA' FOR UPDATE
                """, (r, n) -> r.getLong(1), targetId).stream().findFirst()
                .orElseThrow(() -> CuentaException.forbidden(
                        "Debes esperar la verificación del titular antes de reactivar."));
        var sol = jdbc.query("""
                SELECT id_solicitud, vence_utc FROM mycar.solicitud_eliminacion
                 WHERE id_usuario=? AND estado='VIGENTE' FOR UPDATE
                """, (r, n) -> new Object[]{r.getLong(1), r.getTimestamp(2).toInstant()}, targetId)
                .stream().findFirst()
                .orElseThrow(() -> CuentaException.estadoInvalido("No hay solicitud vigente."));
        Instant vence = (Instant) sol[1];
        if (!now.isBefore(vence)) {
            throw CuentaException.estadoInvalido("El plazo de 30 días ha vencido. No es posible recuperar la cuenta.");
        }

        String destino = CuentaEstados.ACTIVA;
        String suspensionMarca = jdbc.query("""
                SELECT estado_anterior FROM mycar.historial_estado_cuenta
                 WHERE id_usuario_afectado=? AND accion='SOLICITAR_ELIMINACION'
                 ORDER BY creado_utc DESC LIMIT 1
                """, (r, n) -> r.getString(1), targetId).stream().findFirst().orElse(CuentaEstados.ACTIVA);
        if (CuentaEstados.SUSPENDIDA.equals(suspensionMarca)) {
            destino = CuentaEstados.SUSPENDIDA;
        }

        jdbc.update("""
                UPDATE mycar.solicitud_eliminacion
                   SET estado='CANCELADA', cancelada_utc=?, id_actor_admin=?
                 WHERE id_solicitud=?
                """, utc(now), adminId, sol[0]);
        jdbc.update("""
                UPDATE mycar.usuario
                   SET estado_cuenta=?, eliminacion_programada_utc=NULL,
                       motivo_estado=?, observacion_estado=?
                 WHERE id_usuario=?
                """, destino, motivo.strip(), observacion, targetId);
        jdbc.update("""
                UPDATE mycar.verificacion_reactivacion_asistida
                   SET estado='CONSUMIDA', consumida_utc=?
                 WHERE id_verificacion=?
                """, utc(now), ver);
        // No restaurar sesiones previas; el titular inicia sesión nueva.
        sesiones.revocarTodas(targetId, now);
        auditoria.registrar(targetId, null, adminId, "REACTIVAR_CUENTA_ASISTIDA",
                CuentaEstados.PENDIENTE_ELIMINACION, destino, motivo.strip(), observacion, "OK");

        String correo = (String) u[0];
        String aviso = "AVISO: solicitud de eliminacion cancelada. Ya puedes iniciar sesion en MyCar.";
        try {
            mail.send(new MailGateway.Message(
                    correo, aviso, "reactivacion-asistida/" + targetId + "/" + now.toEpochMilli()));
        } catch (RuntimeException ex) {
            log.warn("aviso_reactivacion_asistida_mail_fallo user={}", targetId);
        }
        SimulatedMailInbox bandeja = inbox.getIfAvailable();
        if (bandeja != null && bandeja.enabled()) {
            bandeja.registrar(correo, "AVISO_REACTIVACION",
                    "MyCar — Tu cuenta fue recuperada",
                    aviso, null, null, targetId, now, null);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultado", "CUENTA_RECUPERADA");
        out.put("estado", destino);
        out.put("mensaje", CuentaEstados.SUSPENDIDA.equals(destino)
                ? "Cuenta recuperada. La suspensión administrativa sigue vigente."
                : "Cuenta recuperada. El titular debe iniciar una sesión nueva.");
        return out;
    }

    /** Expone OTP solo al titular en modo desarrollo tras identificar correo con verificación pendiente. */
    public Map<String, Object> estadoTitularPublico(String correoRaw, Instant now) {
        String correo = normalize(correoRaw);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pendiente", false);
        if (correo == null) return out;
        var rows = jdbc.query("""
                SELECT id_usuario FROM mycar.usuario
                 WHERE lower(btrim(correo))=? AND estado_cuenta='PENDIENTE_ELIMINACION'
                """, (r, n) -> r.getLong(1), correo);
        if (rows.isEmpty()) return out;
        long userId = rows.getFirst();
        var ver = jdbc.query("""
                SELECT id_verificacion, expira_utc FROM mycar.verificacion_reactivacion_asistida
                 WHERE id_usuario=? AND estado='PENDIENTE'
                """, (r, n) -> new Object[]{r.getLong(1), r.getTimestamp(2).toInstant()}, userId)
                .stream().findFirst();
        if (ver.isEmpty() || !now.isBefore((Instant) ver.get()[1])) return out;
        out.put("pendiente", true);
        out.put("mensaje", "Hay una verificación de Soporte pendiente. Introduce el código enviado a tu correo.");
        return out;
    }

    private static String normalize(String raw) {
        if (raw == null) return null;
        String correo = raw.strip().toLowerCase(Locale.ROOT);
        if (correo.length() < 5 || !correo.contains("@")) return null;
        return correo;
    }

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}
