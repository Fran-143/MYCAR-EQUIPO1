package backend.vehiculos.dto;

import java.util.UUID;

public record DatosRegistroVehiculo(
        UUID idOperacion,
        String marca,
        String modelo,
        String usoVehiculoCodigo,
        String placa,
        Integer anioFabricacion,
        String color,
        Long idTipoMotor,
        Long idCombustible,
        Integer kilometrajeInicial,
        String numeroVin,
        String numeroSerie,
        String numeroMotor,
        Long idTipoAceite,
        String tipoMotorOtro,
        String combustibleOtro,
        String tipoAceiteOtro
) {
}