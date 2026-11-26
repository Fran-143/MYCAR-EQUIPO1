package backend.licencia.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ContratoLicencia {
    private ContratoLicencia() { }

    public record FotoDto(
            String objectKey,
            String contentType,
            Integer tamanoBytes,
            String nombreOriginal,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean cifrado
    ) {
        public FotoDto(String objectKey, String contentType, Integer tamanoBytes, String nombreOriginal) {
            this(objectKey, contentType, tamanoBytes, nombreOriginal, null);
        }
    }

    public enum AccionFoto { MANTENER, REEMPLAZAR, QUITAR }

    public record LicenciaDto(
            long idLicencia,
            String categoria,
            String numero,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            String vigencia,
            String estadoVigencia,
            boolean recordatorioActivo,
            boolean tieneFotoFrontal,
            boolean tieneFotoPosterior,
            int versionFila
    ) { }

    public record HistorialResumen(
            long idLicencia,
            String categoria,
            String numero,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            String etiqueta
    ) { }

    public record RecordatorioDto(boolean activo, Long idAlerta, String estadoAlerta) { }

    public record ConsultaResponse(
            LicenciaDto actual,
            boolean tieneHistorial,
            List<HistorialResumen> historial,
            RecordatorioDto recordatorio
    ) { }

    public record RegistrarRequest(
            UUID idOperacion,
            String categoria,
            String numero,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            FotoDto fotoFrontal,
            FotoDto fotoPosterior
    ) { }

    public record RegistrarResponse(UUID idOperacion, long idLicencia, String estadoVigencia) { }

    public record EditarRequest(
            String categoria,
            String numero,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            AccionFoto accionFotoFrontal,
            AccionFoto accionFotoPosterior,
            @JsonInclude(JsonInclude.Include.NON_NULL) FotoDto fotoFrontal,
            @JsonInclude(JsonInclude.Include.NON_NULL) FotoDto fotoPosterior
    ) { }

    public record EditarResponse(long idLicencia, int versionFila, String estadoVigencia) { }

    public record RenovarRequest(
            UUID idOperacion,
            long idLicenciaAnterior,
            String categoria,
            String numero,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento,
            @JsonInclude(JsonInclude.Include.NON_NULL) FotoDto fotoFrontal,
            @JsonInclude(JsonInclude.Include.NON_NULL) FotoDto fotoPosterior
    ) { }

    public record RenovarResponse(UUID idOperacion, long idLicenciaAnterior, long idLicenciaNueva, String estadoVigencia) { }

    public record RecordatorioRequest(boolean activo) { }

    public record RecordatorioResponse(long idLicencia, boolean recordatorioActivo, Long idAlerta, String estadoAlerta) { }
}