package backend.historial.model;

public class HistorialNoDisponible extends RuntimeException {
    public HistorialNoDisponible() { super("El vehículo no está disponible para consultar su historial"); }
}
