package backend.mantenimiento.model;

public class ConflictoOperacionMantenimientoException extends IllegalArgumentException {
    public ConflictoOperacionMantenimientoException() {
        super("idOperacion: no se puede reutilizar esta operación para esta solicitud");
    }
}
