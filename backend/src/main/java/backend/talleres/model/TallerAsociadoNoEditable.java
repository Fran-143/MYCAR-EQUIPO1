package backend.talleres.model;

/** RF34-EA09: taller ASOCIADO es de solo lectura (incluidas notas). */
public final class TallerAsociadoNoEditable extends RuntimeException {
    public TallerAsociadoNoEditable() {
        super("Los talleres asociados a MyCar no se pueden modificar");
    }
}
