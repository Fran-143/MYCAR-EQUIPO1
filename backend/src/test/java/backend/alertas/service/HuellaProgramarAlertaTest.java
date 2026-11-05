package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HuellaProgramarAlertaTest {
    @Test void produceSha256De64Hex() {
        var req = new ContratoRf22.ProgramarRequest(
                UUID.randomUUID(), "CAMBIO_ACEITE", "POR_KILOMETRAJE", "DESDE_HOY",
                null, null, 5000, null, null, null, null, null);
        String h = HuellaProgramarAlerta.calcular(3L, req);
        assertEquals(64, h.length());
        assertTrue(h.matches("[a-f0-9]{64}"));
    }

    @Test void mismaSolicitudMismaHuella() {
        UUID op = UUID.randomUUID();
        var req = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", "DESDE_HOY",
                null, null, 5000, null, null, null, null, null);
        assertEquals(HuellaProgramarAlerta.calcular(3L, req), HuellaProgramarAlerta.calcular(3L, req));
    }

    @Test void origenDistintoCambiaHuella() {
        UUID op = UUID.randomUUID();
        var hoy = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", "DESDE_HOY",
                null, null, 5000, null, null, null, null, null);
        var mant = new ContratoRf22.ProgramarRequest(
                op, "CAMBIO_ACEITE", "POR_KILOMETRAJE", "DESDE_MANTENIMIENTO",
                null, null, 5000, null, 55L, null, null, null);
        assertNotEquals(HuellaProgramarAlerta.calcular(3L, hoy), HuellaProgramarAlerta.calcular(3L, mant));
    }
}
