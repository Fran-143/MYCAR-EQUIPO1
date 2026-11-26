package backend.cuentas;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ReactivacionCuentaService {
    private final JdbcTemplate jdbc;
    private final CuentaOtpService otp;
    private final AuditoriaCuenta auditoria;
    private final PasswordEncoder passwords;

    public ReactivacionCuentaService(JdbcTemplate jdbc, CuentaOtpService otp, AuditoriaCuenta auditoria,
            @Qualifier("registrationPasswordEncoder") PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.otp = otp;
        this.auditoria = auditoria;
        this.passwords = passwords;
    }

    @Transactional
    public Map<String, Object> iniciar(String correoRaw, String contrasena, Instant now) {
        String correo = normalize(correoRaw);
        if (correo == null) throw CuentaException.validation(Map.of("correo", "Ingresa un correo válido."));
        var account = jdbc.query("""
                SELECT id_usuario, password_hash, estado_cuenta FROM mycar.usuario
                 WHERE lower(btrim(correo))=? FOR NO KEY UPDATE
                """, (r, n) -> new Object[]{r.getLong(1), r.getString(2), r.getString(3)}, correo)
                .stream().findFirst();
        // Anti-enumeración: respuesta genérica si no existe o credenciales malas.
        if (account.isEmpty() || !passwords.matches(contrasena == null ? "" : contrasena, (String) account.get()[1])) {
            throw CuentaException.validation(Map.of("credenciales", "No se pudo iniciar la recuperación."));
        }
        long userId = (Long) account.get()[0];
        String estado = (String) account.get()[2];
        if (CuentaEstados.ELIMINADA.equals(estado)) {
            throw CuentaException.validation(Map.of("credenciales", "No se pudo iniciar la recuperación."));
        }
        if (!CuentaEstados.PENDIENTE_ELIMINACION.equals(estado)) {
            throw CuentaException.estadoInvalido("La cuenta no está pendiente de eliminación.");
        }
        var sol = jdbc.query("""
                SELECT id_solicitud, vence_utc FROM mycar.solicitud_eliminacion
                 WHERE id_usuario=? AND estado='VIGENTE' FOR UPDATE
                """, (r, n) -> new Object[]{r.getLong(1), r.getTimestamp(2).toInstant()}, userId)
                .stream().findFirst()
                .orElseThrow(() -> CuentaException.estadoInvalido("No hay solicitud vigente."));
        Instant vence = (Instant) sol[1];
        if (!now.isBefore(vence)) {
            throw CuentaException.estadoInvalido("El plazo de 30 días ha vencido. No es posible recuperar la cuenta.");
        }
        var emit = otp.emitir(userId, correo, CuentaEstados.FINALIDAD_REACTIVAR, now);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultado", "OTP_ENVIADO");
        out.put("venceUtc", vence.toString());
        if (emit.codigoDesarrollo() != null) out.put("codigoDesarrollo", emit.codigoDesarrollo());
        return out;
    }

    @Transactional
    public Map<String, Object> verificarOtp(String correoRaw, String codigo, Instant now) {
        long userId = requirePendiente(correoRaw);
        otp.verificar(userId, CuentaEstados.FINALIDAD_REACTIVAR, codigo, now);
        return Map.of("resultado", "OTP_VALIDO");
    }

    @Transactional
    public Map<String, Object> confirmar(String correoRaw, String codigo, Instant now) {
        String correo = normalize(correoRaw);
        long userId = requirePendiente(correo);
        var sol = jdbc.query("""
                SELECT id_solicitud, vence_utc FROM mycar.solicitud_eliminacion
                 WHERE id_usuario=? AND estado='VIGENTE' FOR UPDATE
                """, (r, n) -> new Object[]{r.getLong(1), r.getTimestamp(2).toInstant()}, userId)
                .stream().findFirst()
                .orElseThrow(() -> CuentaException.estadoInvalido("No hay solicitud vigente."));
        Instant vence = (Instant) sol[1];
        if (!now.isBefore(vence)) {
            throw CuentaException.estadoInvalido("El plazo de 30 días ha vencido. No es posible recuperar la cuenta.");
        }
        otp.consumir(userId, CuentaEstados.FINALIDAD_REACTIVAR, codigo, now);

        // Si existía suspensión administrativa independiente registrada en historial reciente
        // mientras PENDIENTE — política: recuperar a ACTIVA salvo marca de suspensión previa en motivo.
        String destino = CuentaEstados.ACTIVA;
        String suspensionMarca = jdbc.query("""
                SELECT estado_anterior FROM mycar.historial_estado_cuenta
                 WHERE id_usuario_afectado=? AND accion='SOLICITAR_ELIMINACION'
                 ORDER BY creado_utc DESC LIMIT 1
                """, (r, n) -> r.getString(1), userId).stream().findFirst().orElse(CuentaEstados.ACTIVA);
        // RF51 no levanta SUSPENDIDA: si el estado anterior a la solicitud era SUSPENDIDA, restaurar SUSPENDIDA.
        if (CuentaEstados.SUSPENDIDA.equals(suspensionMarca)) {
            destino = CuentaEstados.SUSPENDIDA;
        }

        jdbc.update("""
                UPDATE mycar.solicitud_eliminacion
                   SET estado='CANCELADA', cancelada_utc=?
                 WHERE id_solicitud=?
                """, utc(now), sol[0]);
        jdbc.update("""
                UPDATE mycar.usuario
                   SET estado_cuenta=?, eliminacion_programada_utc=NULL, motivo_estado='REACTIVACION_RF51'
                 WHERE id_usuario=?
                """, destino, userId);
        auditoria.registrar(userId, null, userId, "REACTIVAR_CUENTA",
                CuentaEstados.PENDIENTE_ELIMINACION, destino, "RF51", null, "OK");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultado", "CUENTA_RECUPERADA");
        out.put("estado", destino);
        out.put("mensaje", CuentaEstados.SUSPENDIDA.equals(destino)
                ? "Cuenta recuperada. La suspensión administrativa sigue vigente."
                : "Cuenta recuperada. Ya puedes iniciar sesión.");
        return out;
    }

    private long requirePendiente(String correoRaw) {
        String correo = normalize(correoRaw);
        if (correo == null) throw CuentaException.validation(Map.of("correo", "Ingresa un correo válido."));
        var rows = jdbc.query("""
                SELECT id_usuario, estado_cuenta FROM mycar.usuario WHERE lower(btrim(correo))=?
                """, (r, n) -> new Object[]{r.getLong(1), r.getString(2)}, correo);
        if (rows.isEmpty() || !CuentaEstados.PENDIENTE_ELIMINACION.equals(rows.getFirst()[1])) {
            throw CuentaException.validation(Map.of("credenciales", "No se pudo continuar la recuperación."));
        }
        return (Long) rows.getFirst()[0];
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
