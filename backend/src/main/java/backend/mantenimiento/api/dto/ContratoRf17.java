package backend.mantenimiento.api.dto;

import backend.mantenimiento.model.DatosRegistroMantenimiento.OrigenKmServicio;
import backend.mantenimiento.model.ResultadoRegistroMantenimiento;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTO HTTP PROPUESTO, deshabilitado hasta acordar rutas/identidad con el equipo. */
public final class ContratoRf17 {
    private ContratoRf17() { }

    public record RepuestoRequest(
            @NotBlank String nombre,
            String marca,
            @NotNull @Positive Integer cantidad,
            @DecimalMin("0") BigDecimal precioUnitario
    ) { }

    public record RegistroRequest(
            @NotNull UUID idOperacion,
            @NotBlank String codigoTipoMantenimiento,
            @NotNull LocalDate fechaServicio,
            @NotNull @Positive Integer kmServicio,
            @NotNull OrigenKmServicio origenKmServicio,
            @Positive Long idLecturaKmReferencia,
            @DecimalMin("0") BigDecimal costoTotal,
            String servicioOtro,
            @JsonAlias("descripcion") String observaciones,
            LocalDate proximaFechaSugerida,
            @Positive Integer proximoKmSugerido,
            List<@NotNull @Valid RepuestoRequest> repuestos
    ) { }

    public record TipoDto(Long idTipoMantenimiento, String codigo, String nombre) { }
    public record VehiculoDto(
            Long idVehiculo, String placa, String marca, String modelo,
            boolean principal, Long idLecturaActual, Integer kmActual,
            OffsetDateTime fechaLecturaActual, String aceiteRegistrado
    ) { }
    public record LecturaDto(Long idLecturaKm, Integer valorKm, OffsetDateTime fechaLecturaUtc) { }
    public record ContextoResponse(
            LocalDate fechaServidor, String zonaHoraria,
            List<VehiculoDto> vehiculos, List<TipoDto> tipos,
            boolean registroDisponible
    ) { }
    public record RegistroResponse(
            UUID idOperacion, Long idMantenimiento, String estado,
            Integer kmServicio, LocalDate fechaServicio,
            BigDecimal costoTotal, int cantidadRepuestos, boolean alertaCreada
    ) {
        public static RegistroResponse de(ResultadoRegistroMantenimiento r) {
            return new RegistroResponse(r.idOperacion(), r.idMantenimiento(), r.estado(),
                    r.kmServicio(), r.fechaServicio(), r.costoTotal(), r.cantidadRepuestos(), false);
        }
    }
    public record EstadoResponse(UUID idOperacion, String estado, RegistroResponse resultado) { }
    public record ErrorResponse(String codigo, String mensaje,
                                Map<String,String> campos, boolean resultadoIncierto) { }
}
