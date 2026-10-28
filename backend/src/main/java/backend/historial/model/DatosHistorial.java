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
                                Integer kmServicio, String estado, BigDecimal costoTotal) { }

    public record Contexto(List<Vehiculo> vehiculos) {
        public Contexto { vehiculos = List.copyOf(vehiculos); }
    }

    public record Resultado(Vehiculo vehiculo, int total, List<Mantenimiento> mantenimientos) {
        public Resultado { mantenimientos = List.copyOf(mantenimientos); }
    }
}
