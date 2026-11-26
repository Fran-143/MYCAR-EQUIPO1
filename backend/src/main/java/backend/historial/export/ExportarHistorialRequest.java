package backend.historial.export;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record ExportarHistorialRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fechaDesde,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate fechaHasta,
        Long tipoMantenimientoId,
        Boolean incluirAnulados
) {
    public boolean incluirAnuladosEfectivo() {
        return Boolean.TRUE.equals(incluirAnulados);
    }
}
