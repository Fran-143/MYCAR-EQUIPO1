package backend.historial.export;

/** Errores controlados RF26 — exportación PDF. */
public final class ExportarHistorialError extends RuntimeException {
    private final String codigo;

    public ExportarHistorialError(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
