package backend.cuentas;

import backend.localtest.SimulatedMailInbox;
import backend.registro.verificacion.DevMailGateway;
import backend.registro.verificacion.MailGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

@Service
public class CuentaOtpService {
    private static final Logger log = LoggerFactory.getLogger(CuentaOtpService.class);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_FAILURES = 5;

    private final JdbcTemplate jdbc;
    private final MailGateway mail;
    private final ObjectProvider<DevMailGateway> devMail;
    private final ObjectProvider<SimulatedMailInbox> inbox;
    private final boolean desarrollo;
    private final SecureRandom random = new SecureRandom();

    public CuentaOtpService(JdbcTemplate jdbc, MailGateway mail, ObjectProvider<DevMailGateway> devMail,
            ObjectProvider<SimulatedMailInbox> inbox,
            @Value("${mycar.recuperacion.desarrollo:false}") boolean desarrollo) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.devMail = devMail;
        this.inbox = inbox;
        this.desarrollo = desarrollo;
    }

    public record EmitResult(String codigoDesarrollo) { }

    @Transactional
    public EmitResult emitir(long userId, String correo, String finalidad, Instant now) {
        var latest = jdbc.query("""
                SELECT creado_utc FROM mycar.codigo_recuperacion
                 WHERE id_usuario=? AND finalidad=?
                 ORDER BY creado_utc DESC LIMIT 1
                """, (r, n) -> r.getTimestamp(1).toInstant(), userId, finalidad).stream().findFirst();
        if (latest.isPresent() && now.isBefore(latest.get().plus(RESEND_COOLDOWN))) {
            throw CuentaException.validation(Map.of("codigo", "Espera un minuto antes de solicitar otro código."));
        }
        jdbc.update("""
                UPDATE mycar.codigo_recuperacion SET estado='INVALIDADO'
                 WHERE id_usuario=? AND finalidad=? AND estado='ACTIVO'
                """, userId, finalidad);
        String code = String.format("%06d", random.nextInt(1_000_000));
        jdbc.update("""
                INSERT INTO mycar.codigo_recuperacion(
                    id_usuario, codigo_hash, estado, expira_utc, creado_utc, finalidad)
                VALUES (?,?,'ACTIVO',?,?,?)
                """, userId, hash(code), utc(now.plus(TTL)), utc(now), finalidad);
        MailGateway.Outcome outcome;
        try {
            outcome = mail.send(new MailGateway.Message(correo, code, finalidad + "/" + userId + "/" + now.toEpochMilli()));
            if (outcome == null) outcome = new MailGateway.Uncertain();
        } catch (RuntimeException ex) {
            outcome = new MailGateway.Uncertain();
        }
        if (!(outcome instanceof MailGateway.Accepted)) {
            log.warn("cuenta_otp mail_no_aceptado finalidad={}", finalidad);
            throw CuentaException.validation(Map.of("correo", "No se pudo enviar el código. Intenta más tarde."));
        }
        String debug = null;
        if (desarrollo) {
            debug = code;
            DevMailGateway gw = devMail.getIfAvailable();
            if (gw != null) gw.send(new MailGateway.Message(correo, code, finalidad + "/" + userId));
        }
        SimulatedMailInbox bandeja = inbox.getIfAvailable();
        if (bandeja != null && bandeja.enabled()) {
            String tipo = switch (finalidad) {
                case "ELIMINAR_CUENTA" -> "ELIMINAR_CUENTA";
                case "REACTIVAR_CUENTA" -> "REACTIVAR_CUENTA";
                case "VERIF_REACTIV_ADMIN" -> "VERIF_REACTIV_ADMIN";
                default -> "RECUPERAR_CONTRASENA";
            };
            bandeja.registrar(
                    correo,
                    tipo,
                    "MyCar — Código de verificación (" + tipo + ")",
                    "Código de un solo uso para " + tipo + ". No compartas este código.",
                    code,
                    finalidad,
                    userId,
                    now,
                    now.plus(TTL));
        }
        return new EmitResult(debug);
    }

    @Transactional
    public void verificar(long userId, String finalidad, String codigo, Instant now) {
        if (codigo == null || !codigo.matches("\\d{6}")) {
            throw CuentaException.validation(Map.of("codigo", "Ingresa el código de 6 dígitos."));
        }
        var row = jdbc.query("""
                SELECT id_codigo, codigo_hash, expira_utc, intentos_fallidos
                  FROM mycar.codigo_recuperacion
                 WHERE id_usuario=? AND finalidad=? AND estado='ACTIVO'
                 ORDER BY creado_utc DESC LIMIT 1 FOR UPDATE
                """, (r, n) -> new Object[]{r.getLong(1), r.getString(2), r.getTimestamp(3).toInstant(), r.getInt(4)},
                userId, finalidad).stream().findFirst()
                .orElseThrow(() -> CuentaException.validation(Map.of("codigo", "Código inválido.")));
        long id = (Long) row[0];
        String codeHash = (String) row[1];
        Instant expira = (Instant) row[2];
        int failures = (Integer) row[3];
        if (!now.isBefore(expira)) {
            jdbc.update("UPDATE mycar.codigo_recuperacion SET estado='INVALIDADO' WHERE id_codigo=?", id);
            throw CuentaException.validation(Map.of("codigo", "El código ha expirado."));
        }
        if (failures >= MAX_FAILURES) {
            jdbc.update("UPDATE mycar.codigo_recuperacion SET estado='INVALIDADO' WHERE id_codigo=?", id);
            throw CuentaException.validation(Map.of("codigo", "Código inválido."));
        }
        if (!constantTimeEquals(codeHash, hash(codigo))) {
            jdbc.update("UPDATE mycar.codigo_recuperacion SET intentos_fallidos=intentos_fallidos+1 WHERE id_codigo=?", id);
            throw CuentaException.validation(Map.of("codigo", "Código inválido."));
        }
    }

    @Transactional
    public void consumir(long userId, String finalidad, String codigo, Instant now) {
        verificar(userId, finalidad, codigo, now);
        jdbc.update("""
                UPDATE mycar.codigo_recuperacion
                   SET estado='CONSUMIDO', consumido_utc=?
                 WHERE id_usuario=? AND finalidad=? AND estado='ACTIVO'
                """, utc(now), userId, finalidad);
    }

    private static String hash(String code) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}
