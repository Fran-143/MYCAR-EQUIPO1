package backend.vehiculos.seguridad;

public class SesionVehiculosNoValidada extends RuntimeException {
    public SesionVehiculosNoValidada() {
        super("Se requiere una sesión válida de MyCar");
    }
}
