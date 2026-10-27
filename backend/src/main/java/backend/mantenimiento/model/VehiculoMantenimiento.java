package backend.mantenimiento.model;

public record VehiculoMantenimiento(
        Long idVehiculo,
        Long idUsuario,
        String estadoVehiculo
) {
}