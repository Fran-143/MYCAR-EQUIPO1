package backend.historial.model;

public class HistorialInconsistente extends RuntimeException {
    public HistorialInconsistente() { super("No se pudo presentar el historial por una inconsistencia de datos"); }
}
