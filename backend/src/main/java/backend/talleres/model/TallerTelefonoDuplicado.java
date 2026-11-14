package backend.talleres.model;

public final class TallerTelefonoDuplicado extends RuntimeException {
    public TallerTelefonoDuplicado() {
        super("Ya tienes un taller manual activo con ese teléfono");
    }
}
