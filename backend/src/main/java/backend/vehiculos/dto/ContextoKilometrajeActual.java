package backend.vehiculos.dto;

import java.time.Instant;

/** Lectura efectiva para vista previa RF14 (solo lectura). */
public record ContextoKilometrajeActual(
        long idVehiculo,
        long idLectura,
        int kmActual,
        Instant fechaLectura,
        int versionKm,
        String estadoVehiculo
) {
}
