package backend.talleres.model;

public final class TallerYaVinculado extends RuntimeException {
    public TallerYaVinculado() {
        super("Este taller ya está en Mis talleres");
    }
}