package backend.documentos;

public class ErrorAlmacenamiento extends RuntimeException {
    private final String codigo;

    public ErrorAlmacenamiento(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}