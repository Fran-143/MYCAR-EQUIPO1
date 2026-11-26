package backend.vehiculos.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import backend.vehiculos.dto.NumerosEnterosRegistroJson.Entero;
import java.util.UUID;

public record DatosCorregirKilometraje(
        UUID idOperacion,
        @JsonDeserialize(using = Entero.class)
        Integer kmCorregido,
        String motivo,
        Long idLecturaBase,
        @JsonDeserialize(using = Entero.class)
        Integer versionBase
) {
}
