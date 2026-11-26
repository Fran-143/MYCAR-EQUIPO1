package backend.registro.verificacion;

import java.util.Locale;
import java.util.Set;

/** Modos de {@code mycar.verificacion.mail.modo}. */
public final class MailModo {
    private static final Set<String> PRODUCCION = Set.of("smtp", "brevo");

    private MailModo() {}

    /** {@code true} cuando la API pública no debe devolver {@code codigoDesarrollo}. */
    public static boolean ocultaCodigoDesarrollo(String modo) {
        if (modo == null || modo.isBlank()) {
            return false;
        }
        return PRODUCCION.contains(modo.trim().toLowerCase(Locale.ROOT));
    }
}
