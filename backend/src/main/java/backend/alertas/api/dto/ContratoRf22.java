package backend.alertas.api.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contrato HTTP RF22/RF23 (origen de cálculo dual DESDE_HOY | DESDE_MANTENIMIENTO). */
public final class ContratoRf22 {
    private ContratoRf22() { }

    /**
     * Bases confirmadas en el resumen (G89): el servidor las revalida antes de persistir.
     * No forman parte de la huella de idempotencia de la configuración.
     * DESDE_HOY: fechaBaseConfirmada=hoy; kmBaseConfirmado+idLecturaConfirmada=última lectura.
     * DESDE_MANTENIMIENTO: fecha/km confirmados del servicio; idMantenimientoReferencia obligatorio.
     */
    public record ProgramarRequest(
            @NotNull UUID idOperacion,
            @NotBlank String codigoTipoMantenimiento,
            @NotBlank @Pattern(regexp = "POR_TIEMPO|POR_KILOMETRAJE|AMBOS") String criterio,
            @NotBlank @Pattern(regexp = "DESDE_HOY|DESDE_MANTENIMIENTO") String origenCalculo,
            @Pattern(regexp = "SEMANAS|MESES") String unidadTiempo,
            @Positive Integer cantidadTiempo,
            @Positive Integer intervaloKm,
            @Size(max = 160) String servicioOtro,
            @Positive Long idMantenimientoReferencia,
            LocalDate fechaBaseConfirmada,
            Integer kmBaseConfirmado,
            Long idLecturaConfirmada
    ) { }

    public record TipoDto(Long idTipoMantenimiento, String codigo, String nombre) { }
    public record ReferenciaDto(
            Long idMantenimiento,
            String codigoTipo,
            LocalDate fechaServicio,
            Integer kmServicio,
            String servicioOtro
    ) { }
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
            String origenCalculo,
            String estado,
            LocalDate fechaObjetivo,
            Integer kmObjetivo,
            Integer kmBase,
            Integer intervaloKm,
            String servicioOtro,
            int versionFila
    ) { }

    public record ListaResponse(int alertasSincronizadas, List<AlertaDto> alertas) { }

    /** RF24 B1 — detalle de alerta MANTENIMIENTO propia + vehículo + acciones. */
    public record VehiculoResumenDto(Long idVehiculo, String placa, String marca, String modelo, String estadoVehiculo) { }

    public record AccionesDetalleDto(boolean atender, boolean anular) { }

    public record DetalleAlertaDto(
            Long idAlerta,
            String categoria,
            String codigoTipo,
            String nombreTipo,
            String criterio,
            String origenCalculo,
            String estado,
            String fechaEstadoUtc,
            LocalDate fechaObjetivo,
            Integer kmObjetivo,
            Integer kmBase,
            Integer intervaloKm,
            String unidadTiempo,
            Integer cantidadTiempo,
            LocalDate fechaBase,
            String servicioOtro,
            Long idTipoMantenimiento,
            Long idMantenimientoReferencia,
            Long idMantenimientoAtencion,
            int versionFila,
            VehiculoResumenDto vehiculo,
            AccionesDetalleDto acciones
    ) { }

    public record EstadoResponse(UUID idOperacion, String estado, ProgramarResponse resultado) { }

    /** RF24 acción A: registrar mantenimiento nuevo y marcar alerta REALIZADA. */
    public record AtenderRequest(
            @NotNull UUID idOperacion,
            @NotNull LocalDate fechaServicio,
            @NotNull @Positive Integer kmServicio,
            @NotBlank @Pattern(regexp = "LECTURA|HISTORICO_MANUAL") String origenKmServicio,
            @Positive Long idLecturaKmReferencia,
            @DecimalMin("0") java.math.BigDecimal costoTotal,
            String observaciones,
            java.util.List<AtenderRepuesto> repuestos
    ) { }

    public record AtenderRepuesto(
            @NotBlank String nombre,
            String marca,
            @NotNull @Positive Integer cantidad,
            @DecimalMin("0") java.math.BigDecimal precioUnitario
    ) { }

    public record AtenderResponse(
            Long idAlerta, String estadoAlerta, Long idMantenimientoAtencion, UUID idOperacion
    ) { }

    public record AnularAlertaResponse(Long idAlerta, String estado) { }

    public record ErrorResponse(String codigo, String mensaje, Map<String, String> campos, boolean resultadoIncierto) { }
}
