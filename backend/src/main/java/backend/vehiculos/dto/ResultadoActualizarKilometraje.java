package backend.vehiculos.dto;

import java.time.Instant;
import java.util.UUID;

public record ResultadoActualizarKilometraje(
        UUID idOperacion,
        long idVehiculo,
        long idLectura,
        int kmActual,
        Instant fechaServidor,
        String estadoOperacion
) {
}
