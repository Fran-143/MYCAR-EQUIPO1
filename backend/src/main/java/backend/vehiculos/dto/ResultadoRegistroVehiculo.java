package backend.vehiculos.dto;

import java.time.Instant;
import java.util.UUID;

/** Resultado interno persistido. El contrato HTTP sigue siendo una propuesta. */
public record ResultadoRegistroVehiculo(
        UUID idOperacion,
        long idVehiculo,
        String placa,
        boolean principal,
        int kilometrajeActual,
        long idLecturaKm,
        Instant fechaLectura,
        int versionKm
) {
}
