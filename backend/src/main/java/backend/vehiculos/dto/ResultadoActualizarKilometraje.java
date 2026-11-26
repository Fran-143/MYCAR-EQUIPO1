package backend.vehiculos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ResultadoActualizarKilometraje(
        UUID idOperacion,
        long idVehiculo,
        long idLectura,
        BigDecimal kmActual,
        Instant fechaServidor,
        String estadoOperacion
) {
}
