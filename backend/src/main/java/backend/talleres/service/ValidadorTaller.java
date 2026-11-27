package backend.talleres.service;

import jakarta.validation.Validation;
import jakarta.validation.constraints.Email;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ValidadorTaller {
    /** Celular Perú: 9 dígitos y siempre empieza con 9. */
    private static final Pattern TELEFONO_MOVIL_PE = Pattern.compile("^9[0-9]{8}$");
    private final jakarta.validation.Validator emailValidator =
            Validation.buildDefaultValidatorFactory().getValidator();

    public String nombreObligatorio(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("nombre: es obligatorio");
        }
        String n = nombre.trim().toUpperCase(Locale.ROOT);
        if (n.codePointCount(0, n.length()) > 140) {
            throw new IllegalArgumentException("nombre: supera 140 caracteres");
        }
        return n;
    }

    /**
     * Opcional; si se informa: celular Perú (9 dígitos, empieza con 9).
     * Acepta entrada con prefijo 51 / +51 / 0 y la normaliza a 9 dígitos nacionales.
     */
    public String telefonoOpcional(String telefono) {
        if (telefono == null || telefono.isBlank()) return null;
        String digitos = telefono.strip().chars()
                .filter(Character::isDigit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        if (digitos.length() >= 11 && digitos.startsWith("51")) {
            digitos = digitos.substring(2);
        } else if (digitos.length() == 10 && digitos.startsWith("0")) {
            digitos = digitos.substring(1);
        }
        if (!TELEFONO_MOVIL_PE.matcher(digitos).matches()) {
            if (digitos.length() != 9) {
                throw new IllegalArgumentException("Ingresa 9 dígitos (celular Perú)");
            }
            if (!digitos.startsWith("9")) {
                throw new IllegalArgumentException("El celular debe empezar con 9");
            }
            throw new IllegalArgumentException("El teléfono ingresado no es válido");
        }
        return digitos;
    }

    public String correoOpcional(String correo) {
        if (correo == null || correo.isBlank()) return null;
        String c = correo.trim().toLowerCase(Locale.ROOT);
        if (c.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("El correo ingresado no es válido");
        }
        long arrobas = c.chars().filter(ch -> ch == '@').count();
        if (arrobas != 1L) {
            throw new IllegalArgumentException("El correo ingresado no es válido");
        }
        int at = c.indexOf('@');
        if (at <= 0 || at >= c.length() - 1) {
            throw new IllegalArgumentException("El correo ingresado no es válido");
        }
        if (c.codePointCount(0, c.length()) < 5 || c.codePointCount(0, c.length()) > 254) {
            throw new IllegalArgumentException("El correo ingresado no es válido");
        }
        var probe = new EmailProbe(c);
        if (!emailValidator.validate(probe).isEmpty()) {
            throw new IllegalArgumentException("El correo ingresado no es válido");
        }
        return c;
    }

    public String direccionOpcional(String direccion) {
        if (direccion == null || direccion.isBlank()) return null;
        String d = direccion.trim().toUpperCase(Locale.ROOT);
        int len = d.codePointCount(0, d.length());
        if (len < 9) {
            throw new IllegalArgumentException("La dirección debe contener al menos 9 caracteres.");
        }
        if (len > 200) {
            throw new IllegalArgumentException("direccion: supera 200 caracteres");
        }
        return d;
    }

    public String notasOpcional(String notas) {
        if (notas == null || notas.isBlank()) return null;
        String n = notas.trim().toUpperCase(Locale.ROOT);
        if (n.codePointCount(0, n.length()) > 500) {
            throw new IllegalArgumentException("Las notas no pueden superar los 500 caracteres");
        }
        return n;
    }

    /**
     * RF33/RF34: WhatsApp solo con teléfono válido; sin teléfono → false.
     * Casilla desmarcada por defecto.
     */
    public boolean whatsappCoherente(String telefonoNormalizado, Boolean marcado) {
        if (telefonoNormalizado == null || telefonoNormalizado.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(marcado);
    }

    private record EmailProbe(@Email String value) { }
}
