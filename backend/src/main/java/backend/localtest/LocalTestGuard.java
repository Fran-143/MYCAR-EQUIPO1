package backend.localtest;

import java.util.Locale;
import java.util.Set;

public final class LocalTestGuard {
    private static final Set<String> SUFIJOS = Set.of("@mycar.test", "@invalid.mycar.local");

    private LocalTestGuard() { }

    public static void requireEnabled(boolean enabled) {
        if (!enabled) {
            throw new IllegalStateException("Simulador local-test deshabilitado.");
        }
    }

    public static boolean destinatarioPermitido(String correo) {
        if (correo == null || correo.isBlank()) return false;
        String c = correo.strip().toLowerCase(Locale.ROOT);
        for (String s : SUFIJOS) {
            if (c.endsWith(s)) return true;
        }
        return false;
    }

    public static void requireDestinatario(String correo) {
        if (!destinatarioPermitido(correo)) {
            throw new IllegalArgumentException("Solo cuentas ficticias de prueba (@mycar.test).");
        }
    }

    public static boolean esFixtureCorreo(String correo) {
        return destinatarioPermitido(correo);
    }
}
