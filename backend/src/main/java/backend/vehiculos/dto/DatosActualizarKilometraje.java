package backend.vehiculos.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import backend.vehiculos.dto.NumerosEnterosRegistroJson.Entero;
import backend.vehiculos.dto.NumerosEnterosRegistroJson.Kilometraje;
import java.math.BigDecimal;
import java.util.UUID;

public record DatosActualizarKilometraje(
        UUID idOperacion,
        @JsonDeserialize(using = Kilometraje.class)
        BigDecimal kmNuevo,
        Long idLecturaBase,
        @JsonDeserialize(using = Entero.class)
        Integer versionBase
) {
}
