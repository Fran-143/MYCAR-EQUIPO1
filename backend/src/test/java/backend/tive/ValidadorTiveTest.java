package backend.tive;

import backend.tive.api.dto.ContratoTive;
import backend.tive.model.ErrorTive;
import backend.tive.service.ValidadorTive;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorTiveTest {

    @Test
    void vinRechazaIOQ() {
        ErrorTive ex = assertThrows(ErrorTive.class, () -> ValidadorTive.normalizarVin("1HGCM82633O004352"));
        assertEquals("VIN_INVALIDO", ex.codigo());
        assertTrue(ex.getMessage().contains("I, O ni Q"));
    }

    @Test
    void vinCortoMensaje() {
        ErrorTive ex = assertThrows(ErrorTive.class, () -> ValidadorTive.normalizarVin("1HGCM82633A00435"));
        assertTrue(ex.getMessage().contains("16 de 17"));
    }

    @Test
    void vinMayusculas() {
        assertEquals("1HGCM82633A004352", ValidadorTive.normalizarVin("1hgcm82633a004352"));
    }

    @Test
    void vinAusente() {
        ErrorTive ex = assertThrows(ErrorTive.class, () -> ValidadorTive.normalizarVin("  "));
        assertEquals("VIN_AUSENTE", ex.codigo());
    }

    @Test
    void tituloLargo() {
        ErrorTive ex = assertThrows(ErrorTive.class, () -> ValidadorTive.normalizarTitulo("X".repeat(33)));
        assertTrue(ex.getMessage().contains("32"));
    }

    @Test
    void codigoCorto() {
        ErrorTive ex = assertThrows(ErrorTive.class, () -> ValidadorTive.normalizarCodigo("AB"));
        assertTrue(ex.getMessage().contains("al menos 4"));
    }

    @Test
    void motorYSerieOk() {
        assertEquals("ZZR FE123", ValidadorTive.normalizarMotor("zzr fe123"));
        assertEquals("SERIE01", ValidadorTive.normalizarSerie("serie01"));
    }

    @Test
    void registroExigeCampos() {
        var req = new ContratoTive.RegistrarRequest(
                UUID.randomUUID(), null, "ABC", LocalDate.of(2024, 1, 1),
                null, null, "MOTOR1", "SERIE01", null);
        ErrorTive ex = assertThrows(ErrorTive.class, () -> ValidadorTive.exigirRegistro(req));
        assertEquals("DATOS_INVALIDOS", ex.codigo());
        assertTrue(ex.campos().containsKey("numeroTitulo"));
    }

    @Test
    void zonaYSedeJuntas() {
        var req = new ContratoTive.RegistrarRequest(
                UUID.randomUUID(), "TIV-1", "COD1", LocalDate.of(2024, 1, 1),
                "LIMA", null, "MOTOR1", "SERIE01", null);
        ErrorTive ex = assertThrows(ErrorTive.class, () -> ValidadorTive.exigirRegistro(req));
        assertTrue(ex.campos().containsKey("zonaRegistral"));
    }
}
