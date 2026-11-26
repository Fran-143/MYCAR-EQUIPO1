package backend.recordatorioskm.api.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ContratoRecordatoriosKm {
    private ContratoRecordatoriosKm() {
    }

    public record ConfiguracionResponse(
            String estadoRecordatorios,
            List<VehiculoRecordatorioDto> vehiculos
    ) {
    }

    public record VehiculoRecordatorioDto(
            long idVehiculo,
            String marca,
            String modelo,
            String placa,
            boolean tieneFoto,
            BigDecimal kilometrajeActual,
            String frecuencia,
            String proximoRecordatorio,
            boolean sinLecturaValida,
            boolean pendiente
    ) {
    }

    public record EstadoGlobalRequest(String estadoRecordatorios) {
    }

    public record FrecuenciaRequest(String frecuencia) {
    }

    public record PreferenciaVehiculoResponse(
            long idVehiculo,
            String frecuencia,
            String proximoRecordatorio,
            boolean sinLecturaValida,
            boolean pendiente
    ) {
    }

    public record ErrorResponse(String codigo, String mensaje) {
    }
}
