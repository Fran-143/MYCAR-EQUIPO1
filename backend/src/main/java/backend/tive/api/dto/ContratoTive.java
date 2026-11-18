package backend.tive.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;

public final class ContratoTive {
    private ContratoTive() { }

    public record DocumentoDto(
            String objectKey,
            String contentType,
            Integer tamanoBytes,
            String nombreOriginal
    ) { }

    public record TiveDto(
            Long idTive,
            String numeroTitulo,
            String codigoVerificacion,
            LocalDate fechaTitulo,
            String zonaRegistral,
            String sedeRegistral,
            String numeroVin,
            String placa,
            String numeroMotor,
            String numeroSerie,
            boolean tieneDocumento,
            int versionFila,
            DocumentoDto documento
    ) { }

    public record ConsultaResponse(
            TiveDto tive,
            String mensajeDocumento,
            String marca,
            String modelo,
            String placa,
            String numeroVin,
            Integer anioFabricacion,
            String color
    ) { }

    public record RegistrarRequest(
            UUID idOperacion,
            String numeroTitulo,
            String codigoVerificacion,
            LocalDate fechaTitulo,
            String zonaRegistral,
            String sedeRegistral,
            String numeroMotor,
            String numeroSerie,
            DocumentoDto documento
    ) { }

    public record RegistrarResponse(UUID idOperacion, long idTive) { }

    public enum AccionDocumento { MANTENER, AGREGAR, REEMPLAZAR, QUITAR }

    public record ActualizarRequest(
            int versionFila,
            String numeroTitulo,
            String codigoVerificacion,
            LocalDate fechaTitulo,
            String zonaRegistral,
            String sedeRegistral,
            String numeroMotor,
            String numeroSerie,
            AccionDocumento accionDocumento,
            @JsonInclude(JsonInclude.Include.NON_NULL) DocumentoDto documento
    ) { }

    public record ActualizarResponse(long idTive, int versionFila) { }

    public record PrecheckResponse(
            boolean puedeRegistrar,
            boolean tieneVin,
            boolean tieneTive,
            String placa,
            String numeroVin,
            String numeroMotor,
            String numeroSerie,
            String codigoBloqueo,
            String mensaje
    ) { }
}
