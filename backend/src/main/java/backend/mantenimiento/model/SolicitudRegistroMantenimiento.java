package backend.mantenimiento.model;

import java.util.List;
import java.util.UUID;

public record SolicitudRegistroMantenimiento(
        UUID idOperacion,
        Long idVehiculo,
        DatosRegistroMantenimiento datos,
        String observaciones,
        List<DatosRepuesto> repuestos,
        Long idTaller,
        String nombreTallerSnapshot
) {
    public SolicitudRegistroMantenimiento(
            UUID idOperacion,
            Long idVehiculo,
            DatosRegistroMantenimiento datos,
            String observaciones,
            List<DatosRepuesto> repuestos) {
        this(idOperacion, idVehiculo, datos, observaciones, repuestos, null, null);
    }
}