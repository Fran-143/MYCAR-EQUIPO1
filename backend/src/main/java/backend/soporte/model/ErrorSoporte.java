package backend.soporte.model;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ErrorSoporte extends RuntimeException {
    private final String codigo;
    private final Set<String> campos;
    private final boolean incierto;

    public ErrorSoporte(String codigo, String mensaje) {
        this(codigo, mensaje, Set.of(), false);
    }

    public ErrorSoporte(String codigo, String mensaje, Set<String> campos) {
        this(codigo, mensaje, campos, false);
    }

    public ErrorSoporte(String codigo, String mensaje, boolean incierto) {
        this(codigo, mensaje, Set.of(), incierto);
    }

    public ErrorSoporte(String codigo, String mensaje, Set<String> campos, boolean incierto) {
        super(mensaje);
        this.codigo = codigo;
        this.campos = campos;
        this.incierto = incierto;
    }

    public String codigo() { return codigo; }

    public Map<String, String> campos() {
        if (campos.isEmpty()) return Map.of();
        String mensaje = getMessage() == null ? "Revisa este campo." : getMessage();
        return campos.stream().collect(Collectors.toMap(k -> k, k -> mensaje));
    }

    public boolean incierto() { return incierto; }
}
