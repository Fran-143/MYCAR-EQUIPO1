package backend.notificaciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.notificaciones.dao.NotificacionJdbcDao.DetalleRaw;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DetalleNotificacionAssemblerTest {

    @Test
    void soatPendienteMuestraDiasRestantes() {
        var raw = new DetalleRaw(
                1L, "SOAT", "PENDIENTE", false, "t", "c", "DETALLE_ALERTA",
                9L, 3L, "2026-11-20T00:00:00.000Z", "PENDIENTE", "POR_TIEMPO",
                LocalDate.of(2026, 11, 21), null, null, null,
                "Vencimiento de SOAT", "Hyundai Accent", "ABC", null, null, null, null);
        var d = DetalleNotificacionAssembler.ensamblar(raw, LocalDate.of(2026, 11, 20));
        assertEquals(1, d.diasRestantes());
        assertEquals("Ver SOAT", d.accionDisponible());
        assertTrue(d.accionHabilitada());
    }

    @Test
    void soatVencidaDiaSiguiente() {
        var raw = new DetalleRaw(
                2L, "SOAT", "PENDIENTE", false, "t", "c", "DETALLE_ALERTA",
                9L, 3L, "2026-11-21T00:00:00.000Z", "VENCIDA", "POR_TIEMPO",
                LocalDate.of(2026, 11, 20), null, null, null,
                "Vencimiento de SOAT", "Hyundai Accent", "ABC", null, null, null, null);
        var d = DetalleNotificacionAssembler.ensamblar(raw, LocalDate.of(2026, 11, 21));
        assertEquals(1, d.diasTranscurridos());
        assertTrue(d.textoTemporal().contains("Venció"));
    }
}
