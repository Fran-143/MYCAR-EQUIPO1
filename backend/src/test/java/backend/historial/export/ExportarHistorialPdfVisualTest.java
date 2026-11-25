package backend.historial.export;

import backend.historial.export.ExportarHistorialModelos.CabeceraExport;
import backend.historial.export.ExportarHistorialModelos.MantenimientoExport;
import backend.historial.export.ExportarHistorialModelos.RepuestoExport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportarHistorialPdfVisualTest {

    @Test
    void generaPdfVisualDeEjemplo() throws Exception {
        CabeceraExport cab = new CabeceraExport(
                "AVY689", "Toyota", "Corolla", 84500,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 11, 23),
                LocalDate.of(2026, 11, 24));
        List<MantenimientoExport> regs = List.of(
                new MantenimientoExport(
                        1, 1, "ACEITE", "Cambio de aceite", null,
                        LocalDate.of(2026, 9, 10), 82000, "REALIZADO",
                        new BigDecimal("180.50"), "Servicio en taller autorizado.",
                        "Próxima fecha: 2027-03-10", "Taller Central",
                        List.of(new RepuestoExport("Filtro de aceite", "Mann", 1, new BigDecimal("35.00")))),
                new MantenimientoExport(
                        2, 2, "FRENOS", "Revisión de frenos", null,
                        LocalDate.of(2026, 10, 5), 83200, "ANULADO",
                        null, "Registro anulado por error de captura.",
                        null, null, List.of())
        );
        byte[] pdf = ExportarHistorialPdfGenerator.generar(cab, regs);
        assertTrue(pdf.length > 500);
        Path dest = Path.of("target", "rf26-ejemplo-visual.pdf");
        Files.createDirectories(dest.getParent());
        Files.write(dest, pdf);

        byte[] vacio = ExportarHistorialPdfGenerator.generar(cab, List.of());
        assertTrue(vacio.length > 300);
        Files.write(Path.of("target", "rf26-ejemplo-vacio.pdf"), vacio);
    }
}
