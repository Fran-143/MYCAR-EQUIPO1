package backend.mantenimiento.api;

public class IntegracionRf17PendienteException extends RuntimeException {
    public IntegracionRf17PendienteException() {
        super("El registro todavía no está disponible. Conserva el borrador e inténtalo cuando se complete la integración.");
    }
}
