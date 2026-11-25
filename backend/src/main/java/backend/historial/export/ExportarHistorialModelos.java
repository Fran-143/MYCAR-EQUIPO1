package backend.historial.export;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ExportarHistorialModelos {
    private ExportarHistorialModelos() { }

    public record TipoFiltro(long idTipo, String codigo, String nombre) { }

    public record ContextoExport(
            boolean premium,
            long idVehiculo,
            String placa,
            String marca,
            String modelo,
            String estadoVehiculo,
            Integer kmVigente,
            List<TipoFiltro> tipos
    ) {
        public ContextoExport {
            tipos = tipos == null ? List.of() : List.copyOf(tipos);
        }
    }

    public record RepuestoExport(
            String nombreSnapshot,
            String marca,
            Integer cantidad,
            BigDecimal precioUnitario
    ) { }

    public record MantenimientoExport(
            long idMantenimiento,
            long idTipo,
            String codigoTipo,
            String nombreTipo,
            String servicioOtro,
            LocalDate fechaServicio,
            Integer kmServicio,
            String estado,
            BigDecimal costoTotal,
            String descripcion,
            String recomendaciones,
            String nombreTaller,
            List<RepuestoExport> repuestos
    ) {
        public MantenimientoExport {
            repuestos = repuestos == null ? List.of() : List.copyOf(repuestos);
        }
    }

    public record CabeceraExport(
            String placa,
            String marca,
            String modelo,
            Integer kmVigente,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            LocalDate fechaEmisionLima
    ) { }
}
