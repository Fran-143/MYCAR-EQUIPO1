package backend.licencia.service;

import backend.licencia.api.dto.ContratoLicencia;
import backend.licencia.model.ErrorLicencia;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorLicenciaTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Test
    void aceptaVencimientoEnSeisMeses() {
        LocalDate hoy = LocalDate.now(LIMA);
        assertDoesNotThrow(() -> ValidadorLicencia.exigirRegistro(registro(hoy, hoy.plusMonths(6), "Q12345678")));
    }

    @Test
    void aceptaVencimientoEnDiezAnios() {
        LocalDate hoy = LocalDate.now(LIMA);
        assertDoesNotThrow(() -> ValidadorLicencia.exigirRegistro(registro(hoy, hoy.plusYears(10), "Q12345678")));
    }

    @Test
    void rechazaVencimientoAntesDeSeisMeses() {
        LocalDate hoy = LocalDate.now(LIMA);
        ErrorLicencia e = assertThrows(ErrorLicencia.class, () -> ValidadorLicencia.exigirRegistro(
                registro(hoy, hoy.plusMonths(6).minusDays(1), "Q12345678")));
        assertEquals(ValidadorLicencia.MSG_VENCIMIENTO_RANGO, e.getMessage());
        assertTrue(e.campos().containsKey("fechaVencimiento"));
    }

    @Test
    void rechazaVencimientoDespuesDeDiezAnios() {
        LocalDate hoy = LocalDate.now(LIMA);
        ErrorLicencia e = assertThrows(ErrorLicencia.class, () -> ValidadorLicencia.exigirRegistro(
                registro(hoy, hoy.plusYears(10).plusDays(1), "Q12345678")));
        assertEquals(ValidadorLicencia.MSG_VENCIMIENTO_RANGO, e.getMessage());
    }

    @Test
    void rechazaEmisionFutura() {
        LocalDate hoy = LocalDate.now(LIMA);
        LocalDate emisionFutura = hoy.plusDays(1);
        ErrorLicencia e = assertThrows(ErrorLicencia.class, () -> ValidadorLicencia.exigirRegistro(
                registro(emisionFutura, emisionFutura.plusMonths(6), "Q12345678")));
        assertEquals(ValidadorLicencia.MSG_EMISION_FUTURA, e.getMessage());
        assertTrue(e.campos().containsKey("fechaEmision"));
    }

    @Test
    void rechazaNumeroConFormatoInvalido() {
        LocalDate hoy = LocalDate.now(LIMA);
        ErrorLicencia guiones = assertThrows(ErrorLicencia.class, () -> ValidadorLicencia.exigirRegistro(
                registro(hoy, hoy.plusYears(1), "Q1234-5678")));
        assertEquals(ValidadorLicencia.MSG_NUMERO, guiones.getMessage());

        ErrorLicencia corto = assertThrows(ErrorLicencia.class, () -> ValidadorLicencia.exigirRegistro(
                registro(hoy, hoy.plusYears(1), "Q1234567")));
        assertEquals(ValidadorLicencia.MSG_NUMERO, corto.getMessage());
    }

    @Test
    void renovacionExigeVencimientoMayorQueAnterior() {
        LocalDate anterior = LocalDate.of(2025, 6, 1);
        ErrorLicencia e = assertThrows(ErrorLicencia.class, () -> ValidadorLicencia.exigirVencimientoRenovacion(
                anterior, anterior));
        assertTrue(e.getMessage().contains("posterior"));
    }

  @Test
  void editarPermiteVencimientoHistoricoVencido() {
      LocalDate emision = LocalDate.of(2018, 1, 10);
      LocalDate vencimiento = LocalDate.of(2019, 1, 10);
      var req = new ContratoLicencia.EditarRequest(
              "A-I", "Q12345678", emision, vencimiento,
              ContratoLicencia.AccionFoto.MANTENER, ContratoLicencia.AccionFoto.MANTENER,
              null, null);
      assertDoesNotThrow(() -> ValidadorLicencia.exigirEditar(req));
    }

    private static ContratoLicencia.RegistrarRequest registro(
            LocalDate emision, LocalDate vencimiento, String numero) {
        var foto = new ContratoLicencia.FotoDto("licencia/1/temp/f.jpg", "image/jpeg", 100, "f.jpg", true);
        return new ContratoLicencia.RegistrarRequest(
                UUID.randomUUID(), "A-I", numero, emision, vencimiento, foto, foto);
    }
}