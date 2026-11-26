package backend.soporte.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ContratoSoporte {
    private ContratoSoporte() { }

    public record VehiculoElegibleDto(
            long idVehiculo,
            String placa,
            String marca,
            String modelo,
            String numeroVin,
            boolean tieneTive
    ) { }

    public record SolicitudResumenDto(
            String codigo,
            String tipo,
            String estado,
            Long idVehiculo,
            String placa,
            String asunto,
            String categoria,
            Instant creadoUtc,
            Instant actualizadoUtc
    ) { }

    public record EventoDto(
            String estadoAnterior,
            String estadoNuevo,
            String observacion,
            Instant ocurridoUtc
    ) { }

    public record SolicitudDetalleDto(
            String codigo,
            String tipo,
            String estado,
            Long idVehiculo,
            String placa,
            String marca,
            String modelo,
            String nombreUsuario,
            String correoUsuario,
            String vinRegistrado,
            String vinPropuesto,
            String motivo,
            String motivoRechazo,
            boolean tieneEvidencia,
            String contentTypeEvidencia,
            String nombreEvidencia,
            Integer tamanoEvidenciaBytes,
            int versionFila,
            Instant creadoUtc,
            Instant actualizadoUtc,
            List<EventoDto> eventos,
            String categoria,
            String temaOtro,
            String asunto,
            String descripcion,
            String respuestaAdmin,
            Instant cerradoUtc
    ) { }

    public record EvidenciaPayload(byte[] bytes, String contentType, String nombreOriginal) { }

    public record CrearResponse(UUID idOperacion, String codigo, String estado) { }

    public record RechazarRequest(String motivo) { }

    public record ObservacionRequest(String observacion) { }

    public record TerminarRequest(String respuesta) { }

    public record AccionResponse(String codigo, String estado, int versionFila) { }

    public record ResumenSoporteDto(boolean tieneSolicitudes, int total, int pendientes) { }

    public record ContadoresIncidenciaDto(
            int pendientes,
            int aceptadas,
            int enProceso,
            int resueltas,
            int rechazadas
    ) { }
}
