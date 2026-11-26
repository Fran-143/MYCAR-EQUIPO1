package backend.vehiculos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Resultado interno persistido. El contrato HTTP sigue siendo una propuesta. */
public record ResultadoRegistroVehiculo(
        UUID idOperacion,
        long idVehiculo,
        String placa,
        boolean principal,
        BigDecimal kilometrajeActual,
        long idLecturaKm,
        Instant fechaLectura,
        int versionKm
) {
}
