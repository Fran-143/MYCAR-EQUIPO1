package backend.mantenimiento.seguridad;

public class SesionMantenimientoNoValidada extends RuntimeException {
    public SesionMantenimientoNoValidada() { super("Se requiere una sesión válida de MyCar"); }
}
