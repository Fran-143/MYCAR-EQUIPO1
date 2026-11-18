package backend.soporte;

import backend.soporte.model.ErrorSoporte;
import backend.soporte.service.ValidadorSoporte;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorSoporteTest {

    @Test
    void vinIgualAlActual() {
        ErrorSoporte ex = assertThrows(ErrorSoporte.class,
                () -> ValidadorSoporte.normalizarVinPropuesto("1HGCM82633A004352", "1hgcm82633a004352"));
        assertEquals("VIN_SIN_CAMBIO", ex.codigo());
        assertTrue(ex.getMessage().contains("diferente"));
    }

    @Test
    void motivoMayusculasYLimite() {
        assertEquals("ERROR DE DIGITACIÓN", ValidadorSoporte.motivo("error de digitación"));
        ErrorSoporte largo = assertThrows(ErrorSoporte.class, () -> ValidadorSoporte.motivo("X".repeat(251)));
        assertTrue(largo.getMessage().contains("250"));
    }

    @Test
    void evidenciaOpcionalAusenteOk() {
        assertDoesNotThrow(() -> ValidadorSoporte.validarEvidenciaOpcional(null, null));
        assertDoesNotThrow(() -> ValidadorSoporte.validarEvidenciaOpcional("image/jpeg", new byte[0]));
    }

    @Test
    void evidenciaMime() {
        ErrorSoporte ex = assertThrows(ErrorSoporte.class,
                () -> ValidadorSoporte.validarEvidenciaOpcional("text/plain", new byte[]{1, 2, 3}));
        assertTrue(ex.getMessage().contains("formato permitido"));
    }
}
