package backend.talleres.model;

/** Un usuario que no es administrador no puede crear ni vincular talleres globales/asociados. */
public final class TallerAsociacionNoAutorizada extends RuntimeException {
    public TallerAsociacionNoAutorizada() {
        super("No puedes asociar un taller al catálogo de MyCar");
    }
}
