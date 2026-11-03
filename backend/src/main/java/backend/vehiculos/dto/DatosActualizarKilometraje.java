package backend.vehiculos.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import backend.vehiculos.dto.NumerosEnterosRegistroJson.Entero;
import java.util.UUID;

public record DatosActualizarKilometraje(
        UUID idOperacion,
        @JsonDeserialize(using = Entero.class)
        Integer kmNuevo,
        Long idLecturaBase,
        @JsonDeserialize(using = Entero.class)
        Integer versionBase
) {
}
