package backend.historial.model;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DatosHistorial {
    private DatosHistorial() { }

    public record Vehiculo(long idVehiculo, String placa, String marca, String modelo,
                           String estado, boolean principal) { }

    public record Mantenimiento(long idMantenimiento, String codigoTipo, String tipo,
                                String servicioOtro, @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fechaServicio,
                                Integer kmServicio, String estado, BigDecimal costoTotal,
                                String taller) {
        public Mantenimiento(long idMantenimiento, String codigoTipo, String tipo,
                             String servicioOtro, LocalDate fechaServicio,
                             Integer kmServicio, String estado, BigDecimal costoTotal) {
            this(idMantenimiento, codigoTipo, tipo, servicioOtro, fechaServicio, kmServicio, estado, costoTotal, null);
        }
    }

    /** RF19: detalle con campos de solo lectura + editables RF20. */
    public record DetalleMantenimiento(
            long idMantenimiento, long idVehiculo, String placa, String marca, String modelo,
            String estadoVehiculo, String codigoTipo, String tipo,
            String servicioOtro, @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fechaServicio,
            Integer kmServicio, String estado, BigDecimal costoTotal,
            String observaciones, String taller,
            Long idTaller,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate proximaFechaSugerida,
            Integer proximoKmSugerido,
            Instant anuladoUtc,
            boolean editable, boolean anulable, boolean bloqueadoPorAlertaActiva,
            List<RepuestoDetalle> repuestos
    ) {
        public DetalleMantenimiento {
            repuestos = repuestos == null ? List.of() : List.copyOf(repuestos);
        }
    }

    public record RepuestoDetalle(String nombre, String marca, Integer cantidad, BigDecimal precioUnitario) { }

    /**
     * RF20: costo, observaciones y taller (catálogo).
     * sinTaller=true → desasocia; idTaller set → asociar ACTIVO; ambos null/omitidos → mantener.
     */
    public record ModificarMantenimientoRequest(
            BigDecimal costoTotal,
            String observaciones,
            Long idTaller,
            Boolean sinTaller
    ) {
        /** Compatibilidad tests antiguos con texto libre (ignorado como entidad). */
        public ModificarMantenimientoRequest(BigDecimal costoTotal, String observaciones, String taller) {
            this(costoTotal, observaciones, null,
                    taller != null && (taller.isBlank() || "Sin taller".equalsIgnoreCase(taller)
                            || "SIN_TALLER".equalsIgnoreCase(taller)));
        }
    }

    public record AnularMantenimientoResponse(long idMantenimiento, String estado, Instant anuladoUtc) {
        public AnularMantenimientoResponse(long idMantenimiento, String estado) {
            this(idMantenimiento, estado, null);
        }
    }

    public record Contexto(List<Vehiculo> vehiculos) {
        public Contexto { vehiculos = List.copyOf(vehiculos); }
    }

    public record Resultado(Vehiculo vehiculo, int total, List<Mantenimiento> mantenimientos) {
        public Resultado { mantenimientos = List.copyOf(mantenimientos); }
    }
}
