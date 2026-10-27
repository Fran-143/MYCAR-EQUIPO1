package backend.registro;

import java.util.Map;

/** Only public-safe messages and field names; never rejected values or SQL details. */
public final class RegistrationException extends RuntimeException {
    public enum Reason { INVALID_INPUT, EMAIL_UNAVAILABLE, REGISTRATION_UNAVAILABLE }
    private final Reason reason;
    private final Map<String, String> fieldErrors;

    private RegistrationException(Reason reason, String message, Map<String, String> errors) {
        super(message);
        this.reason = reason;
        this.fieldErrors = Map.copyOf(errors);
    }

    public static RegistrationException invalid(Map<String, String> errors) {
        return new RegistrationException(Reason.INVALID_INPUT, "Revisa los datos del registro.", errors);
    }

    public static RegistrationException emailUnavailable() {
        return new RegistrationException(Reason.EMAIL_UNAVAILABLE, "Correo no disponible.", Map.of());
    }

    public static RegistrationException unavailable() {
        return new RegistrationException(Reason.REGISTRATION_UNAVAILABLE,
                "No se pudo completar el registro.", Map.of());
    }

    public Reason reason() { return reason; }
    public Map<String, String> fieldErrors() { return fieldErrors; }
}
