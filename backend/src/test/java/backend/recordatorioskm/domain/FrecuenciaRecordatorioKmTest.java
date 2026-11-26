package backend.recordatorioskm.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FrecuenciaRecordatorioKmTest {

    @Test
    void calculaDiasYMesCalendario() {
        LocalDate base = LocalDate.of(2026, 1, 31);
        assertEquals(LocalDate.of(2026, 2, 1), FrecuenciaRecordatorioKm.DIARIO.calcularProximo(base));
        assertEquals(LocalDate.of(2026, 2, 2), FrecuenciaRecordatorioKm.INTERDIARIO.calcularProximo(base));
        assertEquals(LocalDate.of(2026, 2, 7), FrecuenciaRecordatorioKm.SEMANAL.calcularProximo(base));
        assertEquals(LocalDate.of(2026, 2, 15), FrecuenciaRecordatorioKm.QUINCENAL.calcularProximo(base));
        assertEquals(LocalDate.of(2026, 2, 28), FrecuenciaRecordatorioKm.MENSUAL.calcularProximo(base));
    }

    @Test
    void parseRechazaInvalidas() {
        assertTrue(FrecuenciaRecordatorioKm.parse("SEMANAL").isPresent());
        assertTrue(FrecuenciaRecordatorioKm.parse("diario").isPresent());
        assertTrue(FrecuenciaRecordatorioKm.parse("XYZ").isEmpty());
        assertTrue(FrecuenciaRecordatorioKm.parse("").isEmpty());
    }
}
