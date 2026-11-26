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
    void vigenciaAnualAceptaAniversarioYUltimoDia() {
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        assertDoesNotThrow(() -> ValidadorSoat.exigirRegistro(
                registro(hoy, hoy.plusYears(1))));
        assertDoesNotThrow(() -> ValidadorSoat.exigirRegistro(
                registro(hoy, hoy.plusYears(1).minusDays(1))));
    }

    @Test
    void rechazaInicioPosteriorAHoy() {
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        ErrorSoat e = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(
                registro(hoy.plusYears(10), hoy.plusYears(11))));
        assertEquals(ValidadorSoat.MSG_INICIO_FUTURO, e.getMessage());
        assertTrue(e.campos().containsKey("fechaInicio"));
    }

    @Test
    void vigenciaAnualRechazaSeisYDosAnios() {
        ErrorSoat seis = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirCorregir(
                corregir(LocalDate.of(2020, 11, 15), LocalDate.of(2026, 11, 15))));
        assertEquals(ValidadorSoat.MSG_VIGENCIA_ANUAL, seis.getMessage());

        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        ErrorSoat dos = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(
                registro(hoy, hoy.plusYears(2))));
        assertEquals(ValidadorSoat.MSG_VIGENCIA_ANUAL, dos.getMessage());
    }

    @Test
    void vigenciaAnualBisiesto() {
        assertTrue(ValidadorSoat.esVigenciaAnualOrdinaria(
                LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 28)));
        assertTrue(ValidadorSoat.esVigenciaAnualOrdinaria(
                LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 27)));
    }

    @Test
    void rnRechazaVencimientoPasadoEnAlta() {
        ErrorSoat e = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(
                registro(LocalDate.of(2025, 1, 15), LocalDate.of(2026, 1, 15))));
        assertEquals("No puedes registrar un SOAT con fecha de vencimiento pasada", e.getMessage());
    }

    @Test
    void rechazaAseguradoraFueraDeCatalogo() {
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        var req = new ContratoSoat.RegistrarRequest(
                UUID.randomUUID(), "ASEGURADORA FANTASMA", "012-4589",
                hoy, hoy.plusYears(1),
                null, null, null, null);
        ErrorSoat e = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(req));
        assertTrue(e.getMessage().contains("aseguradora") || e.getMessage().contains("catálogo")
                || e.getMessage().toLowerCase().contains("selecciona"));
    }

    @Test
    void catalogoActivoYHistorico() {
        assertTrue(CatalogoAseguradorasSoat.esActiva("VIVIR SEGUROS"));
        assertTrue(CatalogoAseguradorasSoat.esActiva("QUALITAS"));
        assertFalse(CatalogoAseguradorasSoat.esActiva("HDI SEGUROS"));
        assertTrue(CatalogoAseguradorasSoat.esReconocida("HDI SEGUROS"));
    }

    @Test
    void polizaSinLongitudFijaAceptaCerosYGuiones() {
        assertDoesNotThrow(() -> ValidadorSoat.exigirRegistro(
                registroPoliza("001234567890")));
        assertDoesNotThrow(() -> ValidadorSoat.exigirRegistro(
                registroPoliza("012-4589")));
        assertDoesNotThrow(() -> ValidadorSoat.exigirRegistro(
                registroPoliza("AB12-34")));
        // Interseguro: 8 dígitos ilustrativos, no obligatorios
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        assertDoesNotThrow(() -> ValidadorSoat.exigirRegistro(
                new ContratoSoat.RegistrarRequest(
                        UUID.randomUUID(), "INTERSEGURO", "1234567",
                        hoy, hoy.plusYears(1),
                        null, null, null, null)));
    }

    @Test
    void rechazaPolizaConCaracteresInvalidos() {
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        var req = new ContratoSoat.RegistrarRequest(
                UUID.randomUUID(), "RIMAC SEGUROS", "ABC@123",
                hoy, hoy.plusYears(1),
                null, null, null, null);
        ErrorSoat e = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(req));
        assertTrue(e.getMessage().toLowerCase().contains("póliza")
                || e.getMessage().toLowerCase().contains("poliza")
                || e.getMessage().toLowerCase().contains("certificado"));
    }

    @Test
    void rf30Ea07RechazaPrimaNegativaYTope() {
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        var req = new ContratoSoat.RegistrarRequest(
                UUID.randomUUID(), "RIMAC SEGUROS", "012-4589",
                hoy, hoy.plusYears(1),
                null, null, new BigDecimal("-1"), null);
        ErrorSoat e = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(req));
        assertEquals("El importe de la prima no es válido", e.getMessage());
        assertTrue(e.campos().containsKey("importePrima"));

        var tope = new ContratoSoat.RegistrarRequest(
                UUID.randomUUID(), "RIMAC SEGUROS", "012-4589",
                hoy, hoy.plusYears(1),
                null, null, new BigDecimal("1000000.00"), null);
        ErrorSoat e2 = assertThrows(ErrorSoat.class, () -> ValidadorSoat.exigirRegistro(tope));
        assertEquals("El importe de la prima no es válido", e2.getMessage());

        assertDoesNotThrow(() -> ValidadorSoat.exigirRegistro(
                new ContratoSoat.RegistrarRequest(
                        UUID.randomUUID(), "RIMAC SEGUROS", "012-4589",
                        hoy, hoy.plusYears(1),
                        null, null, new BigDecimal("150.50"), null)));
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

        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        ErrorSoat futuro = assertThrows(ErrorSoat.class, () ->
                ValidadorSoat.exigirFechasRenovacion(
                        hoy.plusYears(10), hoy.plusYears(11), iniA, venA));
        assertEquals(ValidadorSoat.MSG_INICIO_FUTURO, futuro.getMessage());
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

    private static ContratoSoat.RegistrarRequest registroPoliza(String poliza) {
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        return new ContratoSoat.RegistrarRequest(
                UUID.randomUUID(), "RIMAC SEGUROS", poliza,
                hoy, hoy.plusYears(1),
                null, null, null, null);
    }

    private static ContratoSoat.CorregirRequest corregir(LocalDate inicio, LocalDate venc) {
        return new ContratoSoat.CorregirRequest(
                "RIMAC SEGUROS", "012-4589", inicio, venc, null, null, null,
                ContratoSoat.AccionDocumento.MANTENER, null);
    }
}
