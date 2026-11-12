package backend.talleres.model;

/** RF37-EA03: fallo al cambiar favorito de forma atómica. */
public final class TallerFavoritoFallido extends RuntimeException {
    public TallerFavoritoFallido() {
        super("No se pudo actualizar el taller favorito, intenta nuevamente");
    }
}
