package backend.registro;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class RegistrationValidator {
    public static final ZoneId REGISTRATION_ZONE = ZoneId.of("America/Lima");
    private static final Pattern SPACES = Pattern.compile("(?U)\\s+");
    private static final Pattern NAME = Pattern.compile(
            "\\p{L}[\\p{L}\\p{M}]*(?:[ '\u2019-]\\p{L}[\\p{L}\\p{M}]*)*");
    private final Clock clock;
    private final Validator validator;

    public RegistrationValidator(@Qualifier("registrationClock") Clock clock, Validator validator) {
        this.clock = clock;
        this.validator = validator;
    }

    public RegistrationData validate(RegistrationCommand command) {
        if (command == null) throw RegistrationException.invalid(Map.of("registro", "Datos obligatorios."));
        Map<String, String> errors = new LinkedHashMap<>();
        String nombre = name(command.nombre(), "nombre", errors);
        String apellido = name(command.apellido(), "apellido", errors);
        String correo = command.correo() == null ? "" : command.correo().strip().toLowerCase(Locale.ROOT);
        if (length(correo) < 5 || length(correo) > 254 || correo.indexOf('@') < 1
                || !validator.validate(new EmailValue(correo)).isEmpty()) {
            errors.put("correo", "Ingresa un correo válido de hasta 254 caracteres.");
        }
        LocalDate birth = command.fechaNacimiento();
        LocalDate today = LocalDate.now(clock.withZone(REGISTRATION_ZONE));
        if (birth == null || birth.isAfter(today)
                || Period.between(birth, today).getYears() < 18
                || Period.between(birth, today).getYears() > 85) {
            errors.put("fechaNacimiento", "La edad debe estar entre 18 y 85 años cumplidos.");
        }
        String password = command.contrasena();
        if (!validPassword(password)) {
            errors.put("contrasena", "Usa 8–64 caracteres con mayúscula, minúscula, dígito y especial, sin espacios.");
        }
        if (password == null || command.confirmacionContrasena() == null
                || !password.equals(command.confirmacionContrasena())) {
            errors.put("confirmacionContrasena", "Las contraseñas deben coincidir exactamente.");
        }
        if (!Boolean.TRUE.equals(command.privacidadAceptada())) {
            errors.put("privacidadAceptada", "Debes aceptar expresamente la política de privacidad.");
        }
        String version = command.versionPrivacidad();
        if (version == null || version.isBlank() || length(version) > 40) {
            errors.put("versionPrivacidad", "Indica la versión aceptada de la política de privacidad.");
        }
        if (!errors.isEmpty()) throw RegistrationException.invalid(errors);
        return new RegistrationData(nombre, apellido, correo, birth, version);
    }

    private static String name(String input, String field, Map<String, String> errors) {
        String normalized = input == null ? "" : SPACES.matcher(input).replaceAll(" ").strip();
        if (length(normalized) < 2 || length(normalized) > 100 || !NAME.matcher(normalized).matches()) {
            errors.put(field, "Usa 2–100 caracteres: letras, espacios y guion o apóstrofo entre letras.");
        }
        // CC-RF01-01: no semantic checks or restricted-word catalog.
        return normalized;
    }

    private static boolean validPassword(String value) {
        if (value == null || length(value) < 8 || length(value) > 64) return false;
        boolean upper = false, lower = false, digit = false, special = false;
        for (int c : value.codePoints().toArray()) {
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) return false;
            upper |= Character.isUpperCase(c);
            lower |= Character.isLowerCase(c);
            digit |= Character.isDigit(c);
            int type = Character.getType(c);
            special |= type == Character.CONNECTOR_PUNCTUATION || type == Character.DASH_PUNCTUATION
                    || type == Character.START_PUNCTUATION || type == Character.END_PUNCTUATION
                    || type == Character.INITIAL_QUOTE_PUNCTUATION || type == Character.FINAL_QUOTE_PUNCTUATION
                    || type == Character.OTHER_PUNCTUATION || type == Character.MATH_SYMBOL
                    || type == Character.CURRENCY_SYMBOL || type == Character.MODIFIER_SYMBOL
                    || type == Character.OTHER_SYMBOL;
        }
        return upper && lower && digit && special;
    }

    private static int length(String value) { return value.codePointCount(0, value.length()); }
    private record EmailValue(@Email String value) { }
}
