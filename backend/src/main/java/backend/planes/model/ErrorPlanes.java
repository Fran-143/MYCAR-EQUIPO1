package backend.planes.model;

public class ErrorPlanes extends RuntimeException {
    private final String codigo;
    private final boolean reintentable;

    public ErrorPlanes(String codigo, String mensaje) {
        this(codigo, mensaje, false);
    }

    public ErrorPlanes(String codigo, String mensaje, boolean reintentable) {
        super(mensaje);
        this.codigo = codigo;
        this.reintentable = reintentable;
    }

    public String codigo() {
        return codigo;
    }

    public boolean reintentable() {
        return reintentable;
    }
}
