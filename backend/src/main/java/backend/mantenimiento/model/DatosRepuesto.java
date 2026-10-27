package backend.mantenimiento.model;

import java.math.BigDecimal;

public record DatosRepuesto(
        String nombre,
        String marca,
        Integer cantidad,
        BigDecimal precioUnitario
) {
}