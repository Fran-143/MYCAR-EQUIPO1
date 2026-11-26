package backend.cuentas;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class EliminacionCuentaService {
    private final JdbcTemplate jdbc;
    private final CuentaOtpService otp;
    private final AuditoriaCuenta auditoria;
    private final SesionRevocacion sesiones;
    private final PasswordEncoder passwords;

    public EliminacionCuentaService(JdbcTemplate jdbc, CuentaOtpService otp, AuditoriaCuenta auditoria,
            SesionRevocacion sesiones, @Qualifier("registrationPasswordEncoder") PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.otp = otp;
        this.auditoria = auditoria;
        this.sesiones = sesiones;
        this.passwords = passwords;
    }

    @Transactional
    public Map<String, Object> iniciar(long userId, Instant now) {
        var u = requireActiva(userId);
        Integer vigentes = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.solicitud_eliminacion WHERE id_usuario=? AND estado='VIGENTE'",
                Integer.class, userId);
        if (vigentes != null && vigentes > 0) {
            throw CuentaException.conflict("Ya existe una solicitud de eliminación vigente.");
        }
        exigirVentana30Dias(userId, now);
        var emit = otp.emitir(userId, u.correo, CuentaEstados.FINALIDAD_ELIMINAR, now);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultado", "OTP_ENVIADO");
        out.put("venceOtpMinutos", 10);
        if (emit.codigoDesarrollo() != null) out.put("codigoDesarrollo", emit.codigoDesarrollo());
        return out;
    }

    /**
     * Máximo una solicitud de eliminación por período móvil de 30 días desde la última solicitada_utc.
     * No se reinicia al recuperar ni por reactivación administrativa.
     */
    private void exigirVentana30Dias(long userId, Instant now) {
        Instant desde = now.minus(30, ChronoUnit.DAYS);
        var ultima = jdbc.query("""
                SELECT solicitada_utc FROM mycar.solicitud_eliminacion
                 WHERE id_usuario=? AND solicitada_utc > ?
                 ORDER BY solicitada_utc DESC LIMIT 1
                """, (r, n) -> r.getTimestamp(1).toInstant(), userId, utc(desde))
                .stream().findFirst();
        if (ultima.isEmpty()) return;
        Instant proxima = ultima.get().plus(30, ChronoUnit.DAYS);
        throw CuentaException.limiteEliminacion(
                "Ya solicitaste eliminar tu cuenta recientemente. Por seguridad, solo puedes realizar una solicitud cada 30 días.",
                Map.of("proximaSolicitudUtc", proxima.toString()));
    }

    @Transactional
    public Map<String, Object> verificarOtp(long userId, String codigo, Instant now) {
        requireActiva(userId);
        otp.verificar(userId, CuentaEstados.FINALIDAD_ELIMINAR, codigo, now);
        return Map.of("resultado", "OTP_VALIDO");
    }

    @Transactional
    public Map<String, Object> confirmar(long userId, String codigo, String motivoCodigo, String motivoTexto, Instant now) {
        var u = requireActiva(userId);
        otp.consumir(userId, CuentaEstados.FINALIDAD_ELIMINAR, codigo, now);
        Integer vigentes = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.solicitud_eliminacion WHERE id_usuario=? AND estado='VIGENTE'",
                Integer.class, userId);
        if (vigentes != null && vigentes > 0) {
            throw CuentaException.conflict("Ya existe una solicitud de eliminación vigente.");
        }
        exigirVentana30Dias(userId, now);
        Instant vence = now.plus(30, ChronoUnit.DAYS);
        Long idSol = jdbc.queryForObject("""
                INSERT INTO mycar.solicitud_eliminacion(
                    id_usuario, estado, motivo_codigo, motivo_texto, solicitada_utc, vence_utc, origen)
                VALUES (?,'VIGENTE',?,?,?,?,'TITULAR') RETURNING id_solicitud
                """, Long.class, userId, motivoCodigo, motivoTexto, utc(now), utc(vence));
        jdbc.update("""
                UPDATE mycar.usuario
                   SET estado_cuenta='PENDIENTE_ELIMINACION', eliminacion_programada_utc=?,
                       motivo_estado=COALESCE(?, 'SOLICITUD_TITULAR')
                 WHERE id_usuario=?
                """, utc(vence), motivoCodigo, userId);
        sesiones.revocarTodas(userId, now);
        sesiones.desactivarPush(userId);
        auditoria.registrar(userId, null, userId, "SOLICITAR_ELIMINACION",
                CuentaEstados.ACTIVA, CuentaEstados.PENDIENTE_ELIMINACION,
                motivoCodigo == null ? "SOLICITUD_TITULAR" : motivoCodigo, null, "OK");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultado", "SOLICITUD_REGISTRADA");
        out.put("idSolicitud", idSol);
        out.put("venceUtc", vence.toString());
        out.put("dias", 30);
        return out;
    }

    private record UserMail(String correo, String estado) { }

    private UserMail requireActiva(long userId) {
        var rows = jdbc.query("""
                SELECT correo, estado_cuenta FROM mycar.usuario WHERE id_usuario=? FOR NO KEY UPDATE
                """, (r, n) -> new UserMail(r.getString(1), r.getString(2)), userId);
        if (rows.isEmpty()) throw CuentaException.notFound();
        if (!CuentaEstados.ACTIVA.equals(rows.getFirst().estado)) {
            throw CuentaException.estadoInvalido("Solo cuentas activas pueden solicitar eliminación.");
        }
        return rows.getFirst();
    }

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /** Anonimización definitiva (job). */
    @Transactional
    public boolean ejecutarSupresion(long userId, Instant now) {
        var rows = jdbc.query("""
                SELECT u.estado_cuenta, u.correo, s.id_solicitud, s.vence_utc, s.estado
                  FROM mycar.usuario u
                  JOIN mycar.solicitud_eliminacion s ON s.id_usuario=u.id_usuario AND s.estado='VIGENTE'
                 WHERE u.id_usuario=?
                 FOR UPDATE OF u, s
                """, (r, n) -> new Object[]{
                r.getString(1), r.getString(2), r.getLong(3), r.getTimestamp(4).toInstant(), r.getString(5)
        }, userId);
        if (rows.isEmpty()) return false;
        Object[] row = rows.getFirst();
        Instant vence = (Instant) row[3];
        if (!now.isAfter(vence) && !now.equals(vence)) {
            // estricto: ejecutar solo si ahora >= vence (calendario vencido)
            if (now.isBefore(vence)) return false;
        }
        String constancia = "ELIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String anonMail = "eliminado+" + constancia.toLowerCase() + "@invalid.mycar.local";
        String hash = passwords.encode(UUID.randomUUID().toString() + "!" + now.toEpochMilli());
        jdbc.update("""
                UPDATE mycar.usuario
                   SET estado_cuenta='ELIMINADA',
                       correo=?,
                       password_hash=?,
                       nombre='Eliminado',
                       apellido='Anonimo',
                       fecha_nacimiento=?,
                       motivo_estado='SUPRESION_VENCIDA',
                       observacion_estado=NULL,
                       tipo_bloqueo=NULL,
                       bloqueo_admin_hasta_utc=NULL,
                       login_bloqueo_hasta_utc=NULL,
                       eliminacion_programada_utc=NULL,
                       constancia_eliminacion=?
                 WHERE id_usuario=?
                """, anonMail, hash, LocalDate.of(1990, 1, 1), constancia, userId);
        jdbc.update("""
                UPDATE mycar.solicitud_eliminacion
                   SET estado='EJECUTADA', ejecutada_utc=?
                 WHERE id_solicitud=?
                """, utc(now), row[2]);
        sesiones.revocarTodas(userId, now);
        sesiones.desactivarPush(userId);
        try {
            jdbc.update("DELETE FROM mycar.codigo_recuperacion WHERE id_usuario=?", userId);
        } catch (Exception ignored) { }
        auditoria.registrar(null, constancia, null, "ELIMINACION_DEFINITIVA",
                CuentaEstados.PENDIENTE_ELIMINACION, CuentaEstados.ELIMINADA,
                "SUPRESION_VENCIDA", null, "OK");
        return true;
    }
}
