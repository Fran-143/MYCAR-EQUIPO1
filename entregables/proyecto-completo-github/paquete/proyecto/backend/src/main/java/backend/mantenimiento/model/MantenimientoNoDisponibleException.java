package backend.mantenimiento.model;

public class MantenimientoNoDisponibleException extends IllegalArgumentException {
    public MantenimientoNoDisponibleException() {
        super("El vehículo no está disponible para registrar un mantenimiento");
    }
}
