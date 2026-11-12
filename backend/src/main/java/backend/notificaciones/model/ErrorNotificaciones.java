package backend.notificaciones.model;

public class ErrorNotificaciones extends RuntimeException {
    private final String codigo;

    public ErrorNotificaciones(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
