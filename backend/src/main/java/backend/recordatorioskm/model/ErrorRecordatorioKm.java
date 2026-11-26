package backend.recordatorioskm.model;

public class ErrorRecordatorioKm extends RuntimeException {
    private final String codigo;

    public ErrorRecordatorioKm(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
