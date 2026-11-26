package backend.tive.model;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ErrorTive extends RuntimeException {
    private final String codigo;
    private final Set<String> campos;
    private final boolean incierto;

    public ErrorTive(String codigo, String mensaje) {
        this(codigo, mensaje, Set.of(), false);
    }

    public ErrorTive(String codigo, String mensaje, Set<String> campos) {
        this(codigo, mensaje, campos, false);
    }

    public ErrorTive(String codigo, String mensaje, boolean incierto) {
        this(codigo, mensaje, Set.of(), incierto);
    }

    public ErrorTive(String codigo, String mensaje, Set<String> campos, boolean incierto) {
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
