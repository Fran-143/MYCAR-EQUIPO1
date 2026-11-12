package backend.vehiculos.dto;

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
        java.math.BigDecimal kilometrajeActual,
        /** ISO-8601 UTC; String para que Gson/Android no falle con Instant numérico. */
        String fechaUltimaLecturaKm,
        String numeroVin,
        String numeroSerie,
        String numeroMotor,
        String tipoAceite,
        boolean motorElectrico
) {
}
