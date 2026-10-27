package backend.vehiculos.dto;

import java.util.UUID;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public record DatosRegistroVehiculo(
        UUID idOperacion,
        String marca,
        String modelo,
        String usoVehiculoCodigo,
        String placa,
        @JsonDeserialize(using = NumerosEnterosRegistroJson.Entero.class)
        Integer anioFabricacion,
        String color,
        @JsonDeserialize(using = NumerosEnterosRegistroJson.Identificador.class)
        Long idTipoMotor,
        @JsonDeserialize(using = NumerosEnterosRegistroJson.Identificador.class)
        Long idCombustible,
        @JsonDeserialize(using = NumerosEnterosRegistroJson.Entero.class)
        Integer kilometrajeInicial,
        String numeroVin,
        String numeroSerie,
        String numeroMotor,
        @JsonDeserialize(using = NumerosEnterosRegistroJson.Identificador.class)
        Long idTipoAceite,
        String tipoMotorOtro,
        String combustibleOtro,
        String tipoAceiteOtro
) {
}
