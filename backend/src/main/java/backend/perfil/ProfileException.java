package backend.perfil;

import java.util.Map;

public final class ProfileException extends RuntimeException {
    public enum Reason { VALIDATION, NOT_FOUND, UNAUTHORIZED_OPERATION }

    private final Reason reason;
    private final Map<String, String> fieldErrors;

    private ProfileException(Reason reason, String message, Map<String, String> fieldErrors) {
        super(message);
        this.reason = reason;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public static ProfileException validation(Map<String, String> errors) {
        return new ProfileException(Reason.VALIDATION, "Revisa los datos del perfil.", errors);
    }

    public static ProfileException notFound() {
        return new ProfileException(Reason.NOT_FOUND, "No se encontró el perfil.", Map.of());
    }

    public static ProfileException unauthorized() {
        return new ProfileException(Reason.UNAUTHORIZED_OPERATION, "No autorizado.", Map.of());
    }

    public Reason reason() { return reason; }
    public Map<String, String> fieldErrors() { return fieldErrors; }
}
