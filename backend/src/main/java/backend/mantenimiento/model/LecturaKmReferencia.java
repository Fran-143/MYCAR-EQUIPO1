package backend.mantenimiento.model;

import java.time.Instant;

public record LecturaKmReferencia(
        Long idLecturaKm,
        Long idVehiculo,
        Integer valorKm,
        Instant fechaLecturaUtc
) {
}