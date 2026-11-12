package backend.talleres.model;

/** RF34-EA02 / RF35-EA05: ya existe un taller ACTIVO con ese nombre. */
public final class TallerNombreDuplicado extends RuntimeException {
    public TallerNombreDuplicado() {
        super("Ya tienes un taller registrado con este nombre");
    }
}
