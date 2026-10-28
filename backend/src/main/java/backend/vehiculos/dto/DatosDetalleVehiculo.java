package backend.vehiculos.dto;

import java.time.Instant;

/** Contrato técnico RF10: lectura completa de un vehículo propio. */
public record DatosDetalleVehiculo(
        long idVehiculo,
        String placa,
        String marca,
        String modelo,
        int anioFabricacion,
        String usoVehiculo,
        String color,
        String tipoMotor,
        String combustible,
        String estadoVehiculo,
        boolean principal,
        Integer kilometrajeActual,
        Instant fechaUltimaLecturaKm,
        String numeroVin,
        String numeroSerie,
        String numeroMotor,
        String tipoAceite,
        boolean motorElectrico
) {
}
