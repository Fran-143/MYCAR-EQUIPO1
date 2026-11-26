package backend.talleres.model;

public class TallerVersionConflicto extends RuntimeException {
    public TallerVersionConflicto() {
        super("El taller fue modificado por otro proceso. Recarga e inténtalo de nuevo.");
    }
}
