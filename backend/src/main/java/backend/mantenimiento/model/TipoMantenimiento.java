package backend.mantenimiento.model;

public record TipoMantenimiento(
        Long idTipoMantenimiento,
        String codigo,
        String nombre,
        boolean activo
) {
}