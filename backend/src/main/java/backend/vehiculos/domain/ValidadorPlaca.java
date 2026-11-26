package backend.vehiculos.domain;

import java.util.regex.Pattern;

public final class ValidadorPlaca {

    /** A1B234 (L-N-L-NNN) o ABC123 (LLLNNN). Sin espacios ni guiones. */
    private static final Pattern FORMATO =
            Pattern.compile("^(?:[A-Z][0-9][A-Z][0-9]{3}|[A-Z]{3}[0-9]{3})$");

    private ValidadorPlaca() {
    }

    public static boolean esValida(String placa) {
        return placa != null
                && FORMATO.matcher(placa).matches();
    }
}
