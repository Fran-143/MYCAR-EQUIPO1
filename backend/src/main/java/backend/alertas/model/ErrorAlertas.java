package backend.alertas.model;

import java.util.Map;
import java.util.Set;

public class ErrorAlertas extends RuntimeException {
    private final String codigo;
    private final Set<String> campos;
    private final boolean incierto;

    public ErrorAlertas(String codigo, String mensaje) {
        this(codigo, mensaje, Set.of(), false);
    }

    public ErrorAlertas(String codigo, String mensaje, Set<String> campos) {
        this(codigo, mensaje, campos, false);
    }

    public ErrorAlertas(String codigo, String mensaje, boolean incierto) {
        this(codigo, mensaje, Set.of(), incierto);
    }

    public ErrorAlertas(String codigo, String mensaje, Set<String> campos, boolean incierto) {
        super(mensaje);
        this.codigo = codigo;
        this.campos = campos;
        this.incierto = incierto;
    }

    public String codigo() { return codigo; }
    public Map<String, String> campos() {
        return campos.isEmpty() ? Map.of() : campos.stream().collect(
                java.util.stream.Collectors.toMap(k -> k, k -> mensajeCampo(k)));
    }
    public boolean incierto() { return incierto; }

    private static String mensajeCampo(String campo) {
        return switch (campo) {
            case "intervaloKm" -> "Indica un intervalo entero positivo.";
            case "cantidadTiempo" -> "Indica una cantidad entera positiva.";
            case "unidadTiempo" -> "Usa SEMANAS o MESES.";
            case "codigoTipoMantenimiento" -> "Selecciona un tipo activo.";
            case "criterio" -> "Selecciona POR_TIEMPO, POR_KILOMETRAJE o AMBOS.";
            default -> "Revisa este campo.";
        };
    }
}
