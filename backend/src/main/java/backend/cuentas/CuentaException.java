package backend.cuentas;

import java.util.Map;

public final class CuentaException extends RuntimeException {
    public enum Reason {
        VALIDACION, NO_ENCONTRADO, CONFLICTO, FORBIDDEN, MFA_STEP_UP, ESTADO_INVALIDO, BLOQUEO_RECUPERACION
    }

    private final Reason reason;
    private final String codigo;
    private final Map<String, String> fields;
    private final Map<String, Object> extras;

    private CuentaException(Reason reason, String codigo, String message,
            Map<String, String> fields, Map<String, Object> extras) {
        super(message);
        this.reason = reason;
        this.codigo = codigo;
        this.fields = fields == null ? Map.of() : Map.copyOf(fields);
        this.extras = extras == null ? Map.of() : Map.copyOf(extras);
    }

    public static CuentaException validation(Map<String, String> fields) {
        return new CuentaException(Reason.VALIDACION, "VALIDACION", "Revisa los datos ingresados.", fields, null);
    }

    public static CuentaException notFound() {
        return new CuentaException(Reason.NO_ENCONTRADO, "NO_ENCONTRADO", "Usuario no encontrado.", Map.of(), null);
    }

    public static CuentaException conflict(String message) {
        return new CuentaException(Reason.CONFLICTO, "CONFLICTO", message, Map.of(), null);
    }

    public static CuentaException forbidden(String message) {
        return new CuentaException(Reason.FORBIDDEN, "FORBIDDEN", message, Map.of(), null);
    }

    public static CuentaException mfaStepUp() {
        return new CuentaException(Reason.MFA_STEP_UP, "MFA_STEP_UP_REQUERIDO",
                "Debes confirmar MFA reciente (últimos 10 minutos).", Map.of(), null);
    }

    public static CuentaException estadoInvalido(String message) {
        return new CuentaException(Reason.ESTADO_INVALIDO, "ESTADO_INVALIDO", message, Map.of(), null);
    }

    public static CuentaException bloqueoRecuperacion(String message, Map<String, Object> extras) {
        return new CuentaException(Reason.BLOQUEO_RECUPERACION, "BLOQUEO_RECUPERACION", message, Map.of(), extras);
    }

    /** RF07: máximo una solicitud de eliminación cada 30 días móviles. */
    public static CuentaException limiteEliminacion(String message, Map<String, String> fields) {
        return new CuentaException(Reason.CONFLICTO, "LIMITE_ELIMINACION_30D", message, fields, null);
    }

    public Reason reason() { return reason; }
    public String codigo() { return codigo; }
    public Map<String, String> fields() { return fields; }
    public Map<String, Object> extras() { return extras; }
}
