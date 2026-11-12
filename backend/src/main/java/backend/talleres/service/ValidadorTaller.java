package backend.talleres.service;

import jakarta.validation.Validation;
import jakarta.validation.constraints.Email;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ValidadorTaller {
    private static final Pattern SOLO_DIGITOS_9 = Pattern.compile("^[0-9]{9}$");
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

    /** Opcional; si se informa debe ser exactamente 9 dígitos (sin +/espacios/guiones). */
    public String telefonoOpcional(String telefono) {
        if (telefono == null || telefono.isBlank()) return null;
        String digitos = telefono.trim().replaceAll("\\D", "");
        if (!SOLO_DIGITOS_9.matcher(digitos).matches()) {
            throw new IllegalArgumentException("El teléfono ingresado no es válido");
        }
        return digitos;
    }

    public String correoOpcional(String correo) {
        if (correo == null || correo.isBlank()) return null;
        String c = correo.trim().toLowerCase(Locale.ROOT);
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
        if (d.codePointCount(0, d.length()) > 200) {
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

    private record EmailProbe(@Email String value) { }
}
