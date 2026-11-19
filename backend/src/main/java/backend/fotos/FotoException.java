package backend.fotos;

import java.util.Map;

public final class FotoException extends RuntimeException {
    public enum Reason { VALIDATION, NOT_FOUND, CONFLICT, FORBIDDEN, UNAUTHORIZED, STORAGE }

    private final Reason reason;
    private final String codigo;

    private FotoException(Reason reason, String codigo, String message) {
        super(message);
        this.reason = reason;
        this.codigo = codigo;
    }

    public static FotoException validation(String message) {
        return new FotoException(Reason.VALIDATION, "FOTO_INVALIDA", message);
    }

    public static FotoException notFound(String message) {
        return new FotoException(Reason.NOT_FOUND, "FOTO_NO_ENCONTRADA", message);
    }

    public static FotoException conflict(String message) {
        return new FotoException(Reason.CONFLICT, "FOTO_YA_REGISTRADA", message);
    }

    public static FotoException forbidden(String message) {
        return new FotoException(Reason.FORBIDDEN, "FOTO_NO_AUTORIZADA", message);
    }

    public static FotoException unauthorized() {
        return new FotoException(Reason.UNAUTHORIZED, "SESION_INVALIDA", "No autorizado.");
    }

    public static FotoException storage(String message) {
        return new FotoException(Reason.STORAGE, "ALMACENAMIENTO_NO_DISPONIBLE", message);
    }

    public Reason reason() { return reason; }
    public String codigo() { return codigo; }
    public Map<String, String> fieldErrors() {
        return reason == Reason.VALIDATION ? Map.of("foto", getMessage()) : Map.of();
    }
}
