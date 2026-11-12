package backend.recuperacion;

import backend.registro.RegistrationValidator;
import backend.registro.verificacion.DevMailGateway;
import backend.registro.verificacion.MailGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RecoverPassword {
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_FAILURES = 5;
    private final RecoveryStore store;
    private final PasswordEncoder passwords;
    private final MailGateway mail;
    private final ObjectProvider<DevMailGateway> devMail;
    private final boolean desarrollo;
    private final SecureRandom random = new SecureRandom();
    /** Códigos de desarrollo indexados por correo normalizado (solo perfil local/dev). */
    private final ConcurrentHashMap<String, String> lastDevCodes = new ConcurrentHashMap<>();

    public RecoverPassword(RecoveryStore store,
            @Qualifier("registrationPasswordEncoder") PasswordEncoder passwords,
            MailGateway mail,
            ObjectProvider<DevMailGateway> devMail,
            @Value("${mycar.recuperacion.desarrollo:false}") boolean desarrollo) {
        this.store = store;
        this.passwords = passwords;
        this.mail = mail;
        this.devMail = devMail;
        this.desarrollo = desarrollo;
    }

    public record EmitResult(String resultado, String codigoDesarrollo) { }

    @Transactional
    public EmitResult emit(String correoRaw) {
        String correo = requireEmail(correoRaw);
        var account = store.findByEmail(correo);
        if (account.isEmpty()) {
            throw RecoveryException.validation(Map.of("correo",
                    "No hay una cuenta registrada con ese correo."));
        }
        if (!"ACTIVA".equals(account.get().estadoCuenta())) {
            throw RecoveryException.validation(Map.of("correo",
                    "La cuenta no está activa. No se puede recuperar la contraseña."));
        }
        Instant now = store.now();
        var latest = store.findLatest(account.get().userId());
        if (latest.isPresent() && latest.get().createdAt() != null
                && now.isBefore(latest.get().createdAt().plus(RESEND_COOLDOWN))) {
            throw RecoveryException.validation(Map.of("correo",
                    "Espera un minuto antes de solicitar otro código."));
        }
        store.invalidateActive(account.get().userId());
        String code = generateCode();
        store.insertCode(account.get().userId(), hash(code), now.plus(TTL), now);
        mail.send(new MailGateway.Message(correo, code, "rf03/" + account.get().userId() + "/" + now.toEpochMilli()));
        String debug = null;
        if (desarrollo) {
            lastDevCodes.put(correo, code);
            debug = code;
            DevMailGateway gateway = devMail.getIfAvailable();
            if (gateway != null) {
                gateway.send(new MailGateway.Message(correo, code, "rf03/" + account.get().userId()));
            }
        }
        return new EmitResult("CODIGO_ENVIADO_SI_APLICA", debug);
    }

    @Transactional
    public void verify(String correoRaw, String codigo) {
        String correo = requireEmail(correoRaw);
        if (codigo == null || !codigo.matches("\\d{6}")) {
            throw RecoveryException.validation(Map.of("codigo", "Ingresa el código de 6 dígitos."));
        }
        var account = store.findByEmail(correo).orElseThrow(RecoveryException::invalidCode);
        if (!"ACTIVA".equals(account.estadoCuenta())) throw RecoveryException.invalidCode();
        Instant now = store.now();
        var row = store.lockLatestActive(account.userId()).orElseThrow(RecoveryException::invalidCode);
        if (!now.isBefore(row.expiresAt())) {
            store.markInvalidated(row.id());
            throw RecoveryException.expired();
        }
        if (row.failures() >= MAX_FAILURES) {
            store.markInvalidated(row.id());
            throw RecoveryException.invalidCode();
        }
        if (!constantTimeEquals(row.codeHash(), hash(codigo))) {
            store.incrementFailures(row.id());
            throw RecoveryException.invalidCode();
        }
        // Verificación exitosa: el código sigue ACTIVO hasta restablecer (no reutilizable tras consumo).
    }

    @Transactional
    public void resetPassword(String correoRaw, String codigo, String nueva, String confirmacion) {
        verify(correoRaw, codigo);
        Map<String, String> errors = new LinkedHashMap<>();
        if (!RegistrationValidator.isValidPassword(nueva)) {
            errors.put("nuevaContrasena",
                    "Usa 8–64 caracteres con mayúscula, minúscula, dígito y especial, sin espacios.");
        }
        if (nueva == null || confirmacion == null || !nueva.equals(confirmacion)) {
            errors.put("confirmacionContrasena", "Las contraseñas deben coincidir exactamente.");
        }
        if (!errors.isEmpty()) throw RecoveryException.validation(errors);

        String correo = requireEmail(correoRaw);
        var account = store.findByEmail(correo).orElseThrow(RecoveryException::invalidCode);
        // Hash bajo bloqueo: evita reutilizar la contraseña actual (Argon2 con salt distinto parecería "nueva").
        String actualHash = store.lockPasswordHash(account.userId()).orElseThrow(RecoveryException::invalidCode);
        if (passwords.matches(nueva, actualHash)) {
            throw RecoveryException.validation(Map.of("nuevaContrasena",
                    "La nueva contraseña debe ser distinta de la actual."));
        }
        Instant now = store.now();
        var row = store.lockLatestActive(account.userId()).orElseThrow(RecoveryException::invalidCode);
        if (store.updatePassword(account.userId(), passwords.encode(nueva)) != 1) {
            throw RecoveryException.invalidCode();
        }
        store.markConsumed(row.id(), now);
        store.invalidateActive(account.userId());
        lastDevCodes.remove(correo);
    }

    private static String normalizeEmail(String raw) {
        if (raw == null) return null;
        String correo = raw.strip().toLowerCase(Locale.ROOT);
        if (correo.length() < 5 || correo.length() > 254) return null;
        int at = correo.indexOf('@');
        if (at < 1 || at != correo.lastIndexOf('@')) return null;
        String local = correo.substring(0, at);
        String domain = correo.substring(at + 1);
        if (local.isEmpty() || domain.length() < 3 || !domain.contains(".")
                || domain.startsWith(".") || domain.endsWith(".")
                || local.contains(" ") || domain.contains(" ")) {
            return null;
        }
        return correo;
    }

    private static String requireEmail(String raw) {
        String correo = normalizeEmail(raw);
        if (correo == null) throw RecoveryException.validation(Map.of("correo", "Ingresa un correo válido."));
        return correo;
    }

    private String generateCode() {
        int n = random.nextInt(1_000_000);
        return String.format("%06d", n);
    }

    private static String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
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
}
