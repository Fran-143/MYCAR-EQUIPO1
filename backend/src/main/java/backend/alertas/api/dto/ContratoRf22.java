package backend.alertas.api.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contrato HTTP propuesto RF22/RF23 (alineado a RF17 y docs/requisitos). */
public final class ContratoRf22 {
    private ContratoRf22() { }

    public record ProgramarRequest(
            @NotNull UUID idOperacion,
            @NotBlank String codigoTipoMantenimiento,
            @NotBlank @Pattern(regexp = "POR_TIEMPO|POR_KILOMETRAJE|AMBOS") String criterio,
            @Pattern(regexp = "SEMANAS|MESES") String unidadTiempo,
            @Positive Integer cantidadTiempo,
            @Positive Integer intervaloKm,
            @Size(max = 160) String servicioOtro,
            @Positive Long idMantenimientoReferencia
    ) { }

    public record TipoDto(Long idTipoMantenimiento, String codigo, String nombre) { }
    public record ReferenciaDto(Long idMantenimiento, String codigoTipo, LocalDate fechaServicio, String servicioOtro) { }
    public record ContextoResponse(
            LocalDate fechaServidor,
            String zonaHoraria,
            Integer kmActual,
            Long idLecturaActual,
            List<TipoDto> tipos,
            List<ReferenciaDto> referencias,
            boolean programacionDisponible
    ) { }

    public record ProgramarResponse(UUID idOperacion, Long idAlerta, String estado) { }

    public record AlertaDto(
            Long idAlerta,
            String codigoTipo,
            String nombreTipo,
            String criterio,
            String estado,
            LocalDate fechaObjetivo,
            Integer kmObjetivo,
            Integer kmBase,
            Integer intervaloKm,
            String servicioOtro,
            int versionFila
    ) { }

    public record ListaResponse(int alertasSincronizadas, List<AlertaDto> alertas) { }

    public record EstadoResponse(UUID idOperacion, String estado, ProgramarResponse resultado) { }

    public record ErrorResponse(String codigo, String mensaje, Map<String, String> campos, boolean resultadoIncierto) { }
}
