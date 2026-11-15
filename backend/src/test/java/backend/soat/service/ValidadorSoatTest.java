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
    void rnRechazaVencimientoPasadoEnAlta() {
        ErrorSoat e = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(
                registro(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1))));
        assertEquals("No puedes registrar un SOAT con fecha de vencimiento pasada", e.getMessage());
    }

    @Test
    void rechazaAseguradoraFueraDeCatalogo() {
        var req = new ContratoSoat.RegistrarRequest(
                UUID.randomUUID(), "ASEGURADORA FANTASMA", "012-4589",
                LocalDate.of(2026, 12, 1), LocalDate.of(2027, 12, 1),
                null, null, null, null);
        ErrorSoat e = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(req));
        assertTrue(e.getMessage().contains("aseguradora") || e.getMessage().contains("catálogo")
                || e.getMessage().toLowerCase().contains("selecciona"));
    }

    @Test
    void rechazaPolizaConLetras() {
        var req = new ContratoSoat.RegistrarRequest(
                UUID.randomUUID(), "RIMAC SEGUROS", "ABC-123",
                LocalDate.of(2026, 12, 1), LocalDate.of(2027, 12, 1),
                null, null, null, null);
        ErrorSoat e = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(req));
        assertTrue(e.getMessage().toLowerCase().contains("póliza") || e.getMessage().toLowerCase().contains("poliza"));
    }

    @Test
    void rf30Ea07RechazaPrimaNegativaYTope() {
        var req = new ContratoSoat.RegistrarRequest(
                UUID.randomUUID(), "RIMAC SEGUROS", "012-4589",
                LocalDate.of(2026, 12, 1), LocalDate.of(2027, 12, 1),
                null, null, new BigDecimal("-1"), null);
        ErrorSoat e = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(req));
        assertEquals("El importe de la prima no es válido", e.getMessage());
        assertTrue(e.campos().containsKey("importePrima"));

        var tope = new ContratoSoat.RegistrarRequest(
                UUID.randomUUID(), "RIMAC SEGUROS", "012-4589",
                LocalDate.of(2026, 12, 1), LocalDate.of(2027, 12, 1),
                null, null, new BigDecimal("1000000.00"), null);
        ErrorSoat e2 = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(tope));
        assertEquals("El importe de la prima no es válido", e2.getMessage());
    }

    @Test
    void renovarRechazaSiAunNoLlegaVencimiento() {
        LocalDate hoy = LocalDate.of(2026, 11, 15);
        ErrorSoat e = assertThrows(ErrorSoat.class, () ->
                ValidadorSoat.exigirPuedeRenovar(LocalDate.of(2027, 11, 14), hoy));
        assertEquals("SOAT_NO_RENOVABLE", e.codigo());
        assertDoesNotThrow(() ->
                ValidadorSoat.exigirPuedeRenovar(LocalDate.of(2026, 11, 14), hoy));
        assertDoesNotThrow(() ->
                ValidadorSoat.exigirPuedeRenovar(LocalDate.of(2026, 11, 15), hoy));
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
    void coberturaDerivaVencidaSinImpedirConsulta() {
        LocalDate hoy = LocalDate.of(2026, 6, 1);
        assertEquals("VENCIDA", ValidadorSoat.estadoCobertura(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), hoy));
    }

    private static ContratoSoat.RegistrarRequest registro(LocalDate inicio, LocalDate venc) {
        return new ContratoSoat.RegistrarRequest(
                UUID.randomUUID(), "RIMAC SEGUROS", "012-4589", inicio, venc, null, null, null, null);
    }
}
