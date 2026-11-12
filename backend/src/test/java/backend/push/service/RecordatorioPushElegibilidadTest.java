package backend.push.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import backend.push.dao.AlertaPushControlJdbcDao.Control;
import backend.push.dao.RecordatorioPushJdbcDao.AlertaActiva;
import backend.push.service.RecordatorioPushService.Elegibilidad;
import backend.push.service.RecordatorioPushService.Motivo;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecordatorioPushElegibilidadTest {
    private final RecordatorioPushService svc = new RecordatorioPushService(
            null, null, null, null, null, 25, true);
    private final LocalDate hoy = LocalDate.of(2026, 11, 12);

    private static AlertaActiva alerta(String estado) {
        return new AlertaActiva(1L, 2L, 3L, estado, "ABC123", "Aceite");
    }

    @Test
    void proximaSoloEntrada() {
        assertEquals(Motivo.ENTRADA_ESTADO, svc.evaluar(alerta("PROXIMA"), null, hoy).motivo());
        Control ya = new Control(1L, "PROXIMA", hoy.minusDays(1), "ENTRADA_ESTADO", null);
        assertNull(svc.evaluar(alerta("PROXIMA"), ya, hoy));
    }

    @Test
    void pendienteCada3DiasYBootstrap() {
        assertEquals(Motivo.ENTRADA_ESTADO, svc.evaluar(alerta("PENDIENTE"), null, hoy).motivo());
        Control d2 = new Control(1L, "PENDIENTE", hoy.minusDays(2), "ENTRADA_ESTADO", null);
        assertNull(svc.evaluar(alerta("PENDIENTE"), d2, hoy));
        Control d3 = new Control(1L, "PENDIENTE", hoy.minusDays(3), "ENTRADA_ESTADO", null);
        assertEquals(Motivo.RECORDATORIO, svc.evaluar(alerta("PENDIENTE"), d3, hoy).motivo());
    }

    @Test
    void vencidaDiariaSinRafaga() {
        assertEquals(Motivo.ENTRADA_ESTADO, svc.evaluar(alerta("VENCIDA"), null, hoy).motivo());
        Control ayer = new Control(1L, "VENCIDA", hoy.minusDays(1), "ENTRADA_ESTADO", null);
        assertEquals(Motivo.RECORDATORIO, svc.evaluar(alerta("VENCIDA"), ayer, hoy).motivo());
        Control hoyYa = new Control(1L, "VENCIDA", hoy, "RECORDATORIO", null);
        assertNull(svc.evaluar(alerta("VENCIDA"), hoyYa, hoy));
        // 10 días sin envío → un solo elegible hoy (no 10 atrasados)
        Control viejo = new Control(1L, "VENCIDA", hoy.minusDays(10), "RECORDATORIO", null);
        assertEquals(Motivo.RECORDATORIO, svc.evaluar(alerta("VENCIDA"), viejo, hoy).motivo());
    }

    @Test
    void maxUnoPorDiaLocal() {
        Control hoyYa = new Control(1L, "PENDIENTE", hoy, "ENTRADA_ESTADO", null);
        assertNull(svc.evaluar(alerta("VENCIDA"), hoyYa, hoy));
    }
}
