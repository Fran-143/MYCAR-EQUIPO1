package backend.autenticacion;

import java.util.Map;

public final class AuthenticationFailure extends RuntimeException {
    public enum Reason { INVALID_INPUT, INVALID_CREDENTIALS, INVALID_SESSION, ACCOUNT_NOT_ENABLED, UNAVAILABLE }
    private final Reason reason;
    private final Map<String,String> fields;

    private AuthenticationFailure(Reason reason,String message,Map<String,String> fields) {
        super(message); this.reason=reason; this.fields=Map.copyOf(fields);
    }
    public static AuthenticationFailure invalid(Map<String,String> fields) {
        return new AuthenticationFailure(Reason.INVALID_INPUT,"Revisa los datos ingresados.",fields);
    }
    public static AuthenticationFailure credentials() {
        return new AuthenticationFailure(Reason.INVALID_CREDENTIALS,"Correo o contraseña incorrectos.",Map.of());
    }
    public static AuthenticationFailure session() {
        return new AuthenticationFailure(Reason.INVALID_SESSION,"Sesión no válida.",Map.of());
    }
    public static AuthenticationFailure accountNotEnabled() {
        return new AuthenticationFailure(Reason.ACCOUNT_NOT_ENABLED,"La cuenta no está habilitada para iniciar sesión.",Map.of());
    }
    public static AuthenticationFailure unavailable() {
        return new AuthenticationFailure(Reason.UNAVAILABLE,"Autenticación no disponible.",Map.of());
    }
    public Reason reason() { return reason; }
    public Map<String,String> fields() { return fields; }
}
