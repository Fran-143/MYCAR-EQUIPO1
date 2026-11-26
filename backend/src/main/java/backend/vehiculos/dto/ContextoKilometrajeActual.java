package backend.vehiculos.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Lectura efectiva para vista previa RF14 (solo lectura). */
public record ContextoKilometrajeActual(
        long idVehiculo,
        long idLectura,
        BigDecimal kmActual,
        Instant fechaLectura,
        int versionKm,
        String estadoVehiculo
) {
}
