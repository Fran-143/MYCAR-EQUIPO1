package backend.vehiculos.dto;

import java.util.UUID;

public record DatosEstadoOperacionCorreccionKm(
        UUID idOperacion,
        String estado,
        ResultadoCorregirKilometraje resultado
) {
}
