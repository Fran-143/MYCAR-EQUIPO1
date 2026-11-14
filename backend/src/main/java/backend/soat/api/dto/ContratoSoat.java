package backend.soat.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ContratoSoat {
    private ContratoSoat() { }

    public record DocumentoDto(
            String objectKey,
            String contentType,
            Integer tamanoBytes,
            String nombreOriginal
    ) { }

    public record SoatDto(
            Long idSoat,
            String aseguradora,
            String numeroPoliza,
            LocalDate fechaInicio,
            LocalDate fechaVencimiento,
            LocalDate fechaEmision,
            String nombreContratante,
            BigDecimal importePrima,
            String vigencia,
            boolean recordatorioActivo,
            int versionFila,
            DocumentoDto documento
    ) { }

    public record ConsultaResponse(
            SoatDto actual,
            List<SoatDto> historial,
            String estadoCobertura,
            RecordatorioDto recordatorio
    ) { }

    public record RecordatorioDto(boolean activo, Long idAlerta, String estadoAlerta) { }

    public record RegistrarRequest(
            UUID idOperacion,
            String aseguradora,
            String numeroPoliza,
            LocalDate fechaInicio,
            LocalDate fechaVencimiento,
            LocalDate fechaEmision,
            String nombreContratante,
            BigDecimal importePrima,
            DocumentoDto documento
    ) { }

    public record RegistrarResponse(UUID idOperacion, long idSoat, String estadoCobertura) { }

    public enum AccionDocumento { MANTENER, REEMPLAZAR, QUITAR }

    public record CorregirRequest(
            String aseguradora,
            String numeroPoliza,
            LocalDate fechaInicio,
            LocalDate fechaVencimiento,
            LocalDate fechaEmision,
            String nombreContratante,
            BigDecimal importePrima,
            AccionDocumento accionDocumento,
            @JsonInclude(JsonInclude.Include.NON_NULL) DocumentoDto documento
    ) { }

    public record CorregirResponse(long idSoat, int versionFila, String estadoCobertura) { }

    public record RenovarRequest(
            UUID idOperacion,
            long idSoatAnterior,
            String aseguradora,
            String numeroPoliza,
            LocalDate fechaInicio,
            LocalDate fechaVencimiento,
            LocalDate fechaEmision,
            String nombreContratante,
            BigDecimal importePrima,
            DocumentoDto documento
    ) { }

    public record RenovarResponse(UUID idOperacion, long idSoatAnterior, long idSoatNuevo, String estadoCobertura) { }

    public record RecordatorioRequest(boolean activo) { }

    public record RecordatorioResponse(long idSoat, boolean recordatorioActivo, Long idAlerta, String estadoAlerta) { }

    public record PresignUploadRequest(
            String contentType,
            Integer tamanoBytes,
            String nombreOriginal
    ) { }

    public record PresignUploadResponse(
            String objectKey,
            String uploadUrl,
            java.time.Instant expiresAt
    ) { }

    public record DocumentoDescargaResponse(String downloadUrl, java.time.Instant expiresAt, String nombreOriginal) { }
}