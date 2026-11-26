package backend.historial.export;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExportarHistorialPdfServiceTest {

    @Test
    void aceptaExactamenteSeisMeses() {
        LocalDate desde = LocalDate.of(2026, 1, 10);
        LocalDate hasta = LocalDate.of(2026, 7, 10);
        LocalDate hoy = LocalDate.of(2026, 11, 23);
        assertDoesNotThrow(() -> ExportarHistorialPdfService.validarFechas(desde, hasta, hoy));
    }

    @Test
    void rechazaMasDeSeisMeses() {
        LocalDate desde = LocalDate.of(2026, 1, 10);
        LocalDate hasta = LocalDate.of(2026, 7, 11);
        LocalDate hoy = LocalDate.of(2026, 11, 23);
        ExportarHistorialError ex = assertThrows(ExportarHistorialError.class,
                () -> ExportarHistorialPdfService.validarFechas(desde, hasta, hoy));
        assertEquals("RANGO_EXCEDIDO", ex.codigo());
    }

    @Test
    void rechazaFuturo() {
        LocalDate hoy = LocalDate.of(2026, 11, 23);
        ExportarHistorialError ex = assertThrows(ExportarHistorialError.class,
                () -> ExportarHistorialPdfService.validarFechas(
                        LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 24), hoy));
        assertEquals("FECHA_FUTURA", ex.codigo());
    }

    @Test
    void nombreArchivoSaneaPlaca() {
        assertEquals(
                "Historial_MyCar_AVY689_2026-08-15_2026-11-23.pdf",
                ExportarHistorialPdfService.nombreArchivo(
                        "AVY689", LocalDate.of(2026, 8, 15), LocalDate.of(2026, 11, 23)));
    }
}
