package backend.vehiculos.dto;

import java.util.UUID;

public record DatosEstadoOperacionKilometraje(
        UUID idOperacion,
        String estado,
        ResultadoActualizarKilometraje resultado
) {
}
