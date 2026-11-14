package backend.soat.model;

import java.util.Map;
import java.util.Set;

public class ErrorSoat extends RuntimeException {
    private final String codigo;
    private final Set<String> campos;
    private final boolean incierto;

    public ErrorSoat(String codigo, String mensaje) {
        this(codigo, mensaje, Set.of(), false);
    }

    public ErrorSoat(String codigo, String mensaje, Set<String> campos) {
        this(codigo, mensaje, campos, false);
    }

    public ErrorSoat(String codigo, String mensaje, boolean incierto) {
        this(codigo, mensaje, Set.of(), incierto);
    }

    public ErrorSoat(String codigo, String mensaje, Set<String> campos, boolean incierto) {
        super(mensaje);
        this.codigo = codigo;
        this.campos = campos;
        this.incierto = incierto;
    }

    public String codigo() {
        return codigo;
    }

    public Map<String, String> campos() {
        return campos.isEmpty() ? Map.of() : campos.stream().collect(
                java.util.stream.Collectors.toMap(k -> k, k -> "Revisa este campo."));
    }

    public boolean incierto() {
        return incierto;
    }
}