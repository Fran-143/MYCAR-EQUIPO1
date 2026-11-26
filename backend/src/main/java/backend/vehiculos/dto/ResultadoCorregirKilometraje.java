package backend.vehiculos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ResultadoCorregirKilometraje(
        UUID idOperacion,
        long idVehiculo,
        long idLectura,
        long idCorreccion,
        BigDecimal kmAnterior,
        BigDecimal kmActual,
        BigDecimal kmPenultimo,
        String motivo,
        Instant fechaRectificacion,
        Instant fechaLecturaOriginal,
        String estadoOperacion
) {
}
