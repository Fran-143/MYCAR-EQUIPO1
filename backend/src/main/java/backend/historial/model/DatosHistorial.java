package backend.historial.model;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;
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
            long idMantenimiento, long idVehiculo, String codigoTipo, String tipo,
            String servicioOtro, @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fechaServicio,
            Integer kmServicio, String estado, BigDecimal costoTotal,
            String observaciones, String taller,
            boolean editable, boolean anulable, boolean bloqueadoPorAlertaActiva,
            List<RepuestoDetalle> repuestos
    ) {
        public DetalleMantenimiento {
            repuestos = repuestos == null ? List.of() : List.copyOf(repuestos);
        }
    }

    public record RepuestoDetalle(String nombre, String marca, Integer cantidad, BigDecimal precioUnitario) { }

    public record ModificarMantenimientoRequest(BigDecimal costoTotal, String observaciones, String taller) { }

    public record AnularMantenimientoResponse(long idMantenimiento, String estado) { }

    public record Contexto(List<Vehiculo> vehiculos) {
        public Contexto { vehiculos = List.copyOf(vehiculos); }
    }

    public record Resultado(Vehiculo vehiculo, int total, List<Mantenimiento> mantenimientos) {
        public Resultado { mantenimientos = List.copyOf(mantenimientos); }
    }
}
