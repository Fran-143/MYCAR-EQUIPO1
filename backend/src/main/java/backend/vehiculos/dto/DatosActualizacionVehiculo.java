package backend.vehiculos.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * RF11: datos generales editables. No incluye placa ni kilometraje.
 * Si el cliente envía placa o kilometraje en el JSON, Jackson los ignora al no estar en el record;
 * el servicio además rechaza intentos de alterar identificadores protegidos vía comparación con BD.
 */
public record DatosActualizacionVehiculo(
        String marca,
        String modelo,
        String usoVehiculoCodigo,
        @JsonDeserialize(using = NumerosEnterosRegistroJson.Entero.class)
        Integer anioFabricacion,
        String color,
        @JsonDeserialize(using = NumerosEnterosRegistroJson.Identificador.class)
        Long idTipoMotor,
        @JsonDeserialize(using = NumerosEnterosRegistroJson.Identificador.class)
        Long idCombustible,
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
