package backend.mantenimiento.model;

import java.util.List;
import java.util.UUID;

public record SolicitudRegistroMantenimiento(
        UUID idOperacion,
        Long idVehiculo,
        DatosRegistroMantenimiento datos,
        String observaciones,
        List<DatosRepuesto> repuestos
) {
}