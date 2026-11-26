package backend.licencia.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.licencia.model.ErrorLicencia;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ValidadorLicenciaReglasTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Test
    void emisionAnteriorAAnoActualMenos10SeRechaza() {
        LocalDate hoy = LocalDate.now(LIMA);
        LocalDate emision = LocalDate.of(hoy.getYear() - 11, 6, 1);
        LocalDate venc = emision.plusYears(5);
        var ex = assertThrows(ErrorLicencia.class,
                () -> ValidadorLicencia.exigirRegistro(req(emision, venc)));
        assertEquals("DATOS_INVALIDOS", ex.codigo());
        assertTrue(ex.getMessage().contains("año actual menos 10"));
    }

    @Test
    void emisionEnLimiteAnoActualMenos10SeAceptaSiRangoOk() {
        LocalDate hoy = LocalDate.now(LIMA);
        LocalDate emision = LocalDate.of(hoy.getYear() - 10, 1, 1);
        LocalDate venc = emision.plusYears(5);
        if (venc.isBefore(hoy)) {
            // registro permite vencida si rango 6m–10a
            assertDoesNotThrow(() -> ValidadorLicencia.exigirRegistro(req(emision, venc)));
        }
    }

    @Test
    void duplicadoExactoSeRechaza() {
        LocalDate e = LocalDate.of(2020, 1, 10);
        LocalDate v = LocalDate.of(2025, 1, 10);
        var ex = assertThrows(ErrorLicencia.class, () ->
                ValidadorLicencia.exigirNoDuplicadaExacta(
                        "A-I", "Q12345678", e, v,
                        "A-I", "Q12345678", e, v));
        assertEquals("LICENCIA_DUPLICADA", ex.codigo());
    }

    @Test
    void renovacionConVencimientoDistintoNoEsDuplicadoExacto() {
        LocalDate e = LocalDate.of(2020, 1, 10);
        assertDoesNotThrow(() ->
                ValidadorLicencia.exigirNoDuplicadaExacta(
                        "A-I", "Q12345678", e, LocalDate.of(2026, 1, 10),
                        "A-I", "Q12345678", e, LocalDate.of(2025, 1, 10)));
    }

    /**
     * Regla RF23 tiempo (estado_por_tiempo): mañana = PENDIENTE; +8 días = PROXIMA.
     * Documentado aquí para no regresar a una ventana incorrecta en cliente.
     */
    @Test
    void reglaDocumentalMananaEsPendienteYOchoDiasEsProxima() {
        LocalDate hoy = LocalDate.of(2026, 11, 16);
        assertEquals("PENDIENTE", estadoPorTiempo(hoy.plusDays(1), hoy));
        assertEquals("PROXIMA", estadoPorTiempo(hoy.plusDays(8), hoy));
        assertEquals("PENDIENTE", estadoPorTiempo(hoy, hoy));
        assertEquals("PROGRAMADA", estadoPorTiempo(hoy.plusDays(40), hoy));
    }

    /** Espejo de mycar.estado_por_tiempo (V4) para documentos SOAT/Licencia. */
    private static String estadoPorTiempo(LocalDate objetivo, LocalDate hoy) {
        if (!hoy.isBefore(objetivo.plusDays(15))) return "VENCIDA";
        if (!hoy.isBefore(objetivo.minusDays(7))) return "PENDIENTE";
        if (!hoy.isBefore(objetivo.minusDays(30))) return "PROXIMA";
        return "PROGRAMADA";
    }

    private static backend.licencia.api.dto.ContratoLicencia.RegistrarRequest req(
            LocalDate emision, LocalDate venc) {
        var foto = new backend.licencia.api.dto.ContratoLicencia.FotoDto(
                "k", "image/jpeg", 100, "f.jpg", true);
        return new backend.licencia.api.dto.ContratoLicencia.RegistrarRequest(
                java.util.UUID.randomUUID(), "A-I", "Q12345678", emision, venc, foto, foto);
    }
}
