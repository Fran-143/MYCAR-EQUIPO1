package backend.soat.service;

import backend.soat.api.dto.ContratoSoat;
import backend.soat.model.ErrorSoat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorSoatTest {

    @Test
    void rf30Ea01RechazaVencimientoIgualOAnterior() {
        var iguales = registro(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1));
        ErrorSoat e1 = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(iguales));
        assertEquals("DATOS_INVALIDOS", e1.codigo());
        assertEquals("La fecha de vencimiento debe ser posterior a la fecha de inicio", e1.getMessage());

        var anterior = registro(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 30));
        ErrorSoat e2 = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(anterior));
        assertEquals("La fecha de vencimiento debe ser posterior a la fecha de inicio", e2.getMessage());
    }

    @Test
    void rf30Ea01AceptaVencimientoPosterior() {
        assertDoesNotThrow(() -> ValidadorSoat.exigirRegistro(
                registro(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2))));
    }

    @Test
    void rf30Ea07RechazaPrimaNegativa() {
        var req = new ContratoSoat.RegistrarRequest(
                UUID.randomUUID(), "Rimac", "POL-1",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                null, null, new BigDecimal("-1"), null);
        ErrorSoat e = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(req));
        assertEquals("El importe de la prima no es válido", e.getMessage());
        assertTrue(e.campos().containsKey("importePrima"));
    }

    @Test
    void rf31Ea01YEa04FechasRenovacion() {
        LocalDate iniA = LocalDate.of(2025, 1, 1);
        LocalDate venA = LocalDate.of(2026, 1, 1);

        ErrorSoat solape = assertThrows(ErrorSoat.class, () ->
                ValidadorSoat.exigirFechasRenovacion(
                        LocalDate.of(2025, 12, 1), LocalDate.of(2027, 1, 1), iniA, venA));
        assertEquals("SOAT_SOLAPE", solape.codigo());
        assertEquals("La nueva vigencia no puede empezar antes de que termine la actual.", solape.getMessage());

        ErrorSoat venc = assertThrows(ErrorSoat.class, () ->
                ValidadorSoat.exigirFechasRenovacion(
                        LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1), iniA, venA));
        assertEquals("La nueva vigencia debe vencer después de la actual.", venc.getMessage());

        assertDoesNotThrow(() -> ValidadorSoat.exigirFechasRenovacion(
                LocalDate.of(2026, 1, 2), LocalDate.of(2027, 1, 2), iniA, venA));
    }

    @Test
    void coberturaNoRechazaVencimientoPasadoPorSiSolo() {
        LocalDate hoy = LocalDate.of(2026, 6, 1);
        assertEquals("VENCIDA", ValidadorSoat.estadoCobertura(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), hoy));
        assertDoesNotThrow(() -> ValidadorSoat.exigirRegistro(
                registro(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1))));
    }

    private static ContratoSoat.RegistrarRequest registro(LocalDate inicio, LocalDate venc) {
        return new ContratoSoat.RegistrarRequest(
                UUID.randomUUID(), "Rimac", "POL-1", inicio, venc, null, null, null, null);
    }
}
