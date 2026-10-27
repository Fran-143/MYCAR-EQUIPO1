package backend.mantenimiento.model;

import java.util.UUID;

public record OperacionMantenimiento(
        UUID idOperacion,
        Long idUsuario,
        String tipoOperacion,
        String hashSolicitud,
        String estado,
        Long idMantenimiento
) {
}