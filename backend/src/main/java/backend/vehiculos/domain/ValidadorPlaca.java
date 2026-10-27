package backend.vehiculos.domain;

import java.util.regex.Pattern;

public final class ValidadorPlaca {

    private static final Pattern FORMATO =
            Pattern.compile("^[A-Z][0-9][A-Z][0-9]{3}$");

    private ValidadorPlaca() {
    }

    public static boolean esValida(String placa) {
        return placa != null
                && FORMATO.matcher(placa).matches();
    }
}