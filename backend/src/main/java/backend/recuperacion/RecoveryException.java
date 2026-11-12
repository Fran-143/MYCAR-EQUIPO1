package backend.recuperacion;

import java.util.Map;

public final class RecoveryException extends RuntimeException {
    public enum Reason { VALIDATION, INVALID_CODE, EXPIRED, UNAVAILABLE }

    private final Reason reason;
    private final Map<String, String> fieldErrors;

    private RecoveryException(Reason reason, String message, Map<String, String> fieldErrors) {
        super(message);
        this.reason = reason;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public static RecoveryException validation(Map<String, String> errors) {
        return new RecoveryException(Reason.VALIDATION, "Revisa los datos ingresados.", errors);
    }

    public static RecoveryException invalidCode() {
        return new RecoveryException(Reason.INVALID_CODE, "El código no es válido.", Map.of("codigo", "Código incorrecto."));
    }

    public static RecoveryException expired() {
        return new RecoveryException(Reason.EXPIRED, "El código expiró. Solicita uno nuevo.",
                Map.of("codigo", "El código expiró."));
    }

    public Reason reason() { return reason; }
    public Map<String, String> fieldErrors() { return fieldErrors; }
}
