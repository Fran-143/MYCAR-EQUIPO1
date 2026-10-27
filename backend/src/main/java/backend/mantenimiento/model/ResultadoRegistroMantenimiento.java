package backend.mantenimiento.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Snapshot de respuesta conservado en operacion_idempotente al confirmar RF17. */
public record ResultadoRegistroMantenimiento(
        UUID idOperacion, Long idVehiculo, Long idMantenimiento,
        String estado, Integer kmServicio, LocalDate fechaServicio,
        BigDecimal costoTotal, int cantidadRepuestos
) {
    public ResultadoRegistroMantenimiento {
        Objects.requireNonNull(idOperacion);
        Objects.requireNonNull(idVehiculo);
        Objects.requireNonNull(idMantenimiento);
        Objects.requireNonNull(fechaServicio);
        if (idVehiculo <= 0 || idMantenimiento <= 0 || kmServicio == null
                || kmServicio <= 0 || cantidadRepuestos < 0 || !"REALIZADO".equals(estado)) {
            throw new IllegalArgumentException("Resultado de registro inválido");
        }
    }
}
