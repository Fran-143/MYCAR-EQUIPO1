package backend.catalogos;

import java.util.Map;

public final class CatalogoException extends RuntimeException {
    public enum Reason { VALIDACION, NO_ENCONTRADO, CONFLICTO, FORBIDDEN, MFA_STEP_UP, ESTADO_INVALIDO }

    private final Reason reason;
    private final String codigo;
    private final Map<String, String> fields;

    private CatalogoException(Reason reason, String codigo, String message, Map<String, String> fields) {
        super(message);
        this.reason = reason;
        this.codigo = codigo;
        this.fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public static CatalogoException validation(String message) {
        return new CatalogoException(Reason.VALIDACION, "VALIDACION", message, Map.of());
    }

    public static CatalogoException validation(Map<String, String> fields) {
        return new CatalogoException(Reason.VALIDACION, "VALIDACION", "Revisa los datos ingresados.", fields);
    }

    public static CatalogoException notFound(String message) {
        return new CatalogoException(Reason.NO_ENCONTRADO, "NO_ENCONTRADO", message, Map.of());
    }

    public static CatalogoException conflict(String message) {
        return new CatalogoException(Reason.CONFLICTO, "CONFLICTO", message, Map.of());
    }

    public static CatalogoException forbidden(String message) {
        return new CatalogoException(Reason.FORBIDDEN, "FORBIDDEN", message, Map.of());
    }

    public static CatalogoException mfaStepUp() {
        return new CatalogoException(Reason.MFA_STEP_UP, "MFA_STEP_UP_REQUERIDO",
                "Debes confirmar MFA reciente (últimos 10 minutos).", Map.of());
    }

    public static CatalogoException estadoInvalido(String message) {
        return new CatalogoException(Reason.ESTADO_INVALIDO, "ESTADO_INVALIDO", message, Map.of());
    }

    public Reason reason() { return reason; }
    public String codigo() { return codigo; }
    public Map<String, String> fields() { return fields; }
}
