package backend.vehiculos.dto;

import java.util.UUID;

public record DatosEstadoOperacionVehiculo(
        UUID idOperacion,
        String estado,
        ResultadoRegistroVehiculo resultado
) {
}
