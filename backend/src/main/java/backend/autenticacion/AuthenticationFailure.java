package backend.autenticacion;

import java.util.Map;

public final class AuthenticationFailure extends RuntimeException {
    public enum Reason {
        INVALID_INPUT, INVALID_CREDENTIALS, INVALID_SESSION, ACCOUNT_NOT_ENABLED,
        ACCOUNT_SUSPENDED, ACCOUNT_BLOCKED, ACCOUNT_PENDING_DELETION,
        LOGIN_TEMPORARILY_LOCKED, UNAVAILABLE
    }
    private final Reason reason;
    private final Map<String,String> fields;
    private final String codigo;

    private AuthenticationFailure(Reason reason,String codigo,String message,Map<String,String> fields) {
        super(message); this.reason=reason; this.codigo=codigo; this.fields=Map.copyOf(fields);
    }
    public static AuthenticationFailure invalid(Map<String,String> fields) {
        return new AuthenticationFailure(Reason.INVALID_INPUT,"VALIDACION","Revisa los datos ingresados.",fields);
    }
    public static AuthenticationFailure credentials() {
        return new AuthenticationFailure(Reason.INVALID_CREDENTIALS,"CREDENCIALES_INVALIDAS","Correo o contraseña incorrectos.",Map.of());
    }
    public static AuthenticationFailure session() {
        return new AuthenticationFailure(Reason.INVALID_SESSION,"SESION_INVALIDA","Sesión no válida.",Map.of());
    }
    public static AuthenticationFailure accountNotEnabled() {
        return new AuthenticationFailure(Reason.ACCOUNT_NOT_ENABLED,"CUENTA_NO_HABILITADA",
                "La cuenta no está habilitada para iniciar sesión.",Map.of());
    }
    public static AuthenticationFailure suspended() {
        return new AuthenticationFailure(Reason.ACCOUNT_SUSPENDED,"CUENTA_SUSPENDIDA",
                "Tu cuenta está SUSPENDIDA por Administración.",Map.of("accion","CONTACTAR_SOPORTE"));
    }
    public static AuthenticationFailure blocked(String untilIso) {
        // desbloqueoUtc se mantiene en campos internos/auditoría; la UI no debe mostrarlo.
        Map<String,String> f = untilIso == null ? Map.of("accion","CONTACTAR_SOPORTE")
                : Map.of("accion","CONTACTAR_SOPORTE","desbloqueoUtc",untilIso);
        return new AuthenticationFailure(Reason.ACCOUNT_BLOCKED,"CUENTA_BLOQUEADA",
                "Tu cuenta está bloqueada temporalmente por Administración. Contacta con Soporte MyCar si necesitas asistencia.",f);
    }
    public static AuthenticationFailure pendingDeletion(String venceIso, String dias) {
        return new AuthenticationFailure(Reason.ACCOUNT_PENDING_DELETION,"CUENTA_PENDIENTE_ELIMINACION",
                "Tu cuenta está PENDIENTE DE ELIMINACIÓN. Puedes recuperarla dentro del plazo establecido.",
                Map.of("accion","RECUPERAR_CUENTA","venceUtc",venceIso == null ? "" : venceIso,
                        "diasRestantes", dias == null ? "" : dias));
    }
    /** Bloqueo de acceso por intentos fallidos (10 min). Solo tras credenciales válidas. */
    public static AuthenticationFailure loginTemporarilyLocked(String untilIso) {
        Map<String,String> f = untilIso == null ? Map.of("accion","ESPERAR")
                : Map.of("accion","ESPERAR","desbloqueoUtc",untilIso);
        return new AuthenticationFailure(Reason.LOGIN_TEMPORARILY_LOCKED,"LOGIN_BLOQUEADO",
                "Tu cuenta está bloqueada temporalmente por seguridad. Podrás volver a intentarlo en unos minutos.",f);
    }
    public static AuthenticationFailure unavailable() {
        return new AuthenticationFailure(Reason.UNAVAILABLE,"AUTENTICACION_NO_DISPONIBLE","Autenticación no disponible.",Map.of());
    }
    public Reason reason() { return reason; }
    public Map<String,String> fields() { return fields; }
    public String codigo() { return codigo; }
}
