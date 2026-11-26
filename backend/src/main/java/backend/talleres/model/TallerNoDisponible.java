package backend.talleres.model;

public class TallerNoDisponible extends RuntimeException {
    public TallerNoDisponible() { super("Taller no disponible"); }
}
