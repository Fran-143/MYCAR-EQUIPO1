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
            null, null, null, null, null, null, 25, true);
    private final LocalDate hoy = LocalDate.of(2026, 11, 12);

    private static AlertaActiva alerta(String estado) {
        return new AlertaActiva(1L, 2L, 3L, "MANTENIMIENTO", estado, "ABC123", "Aceite",
                LocalDate.of(2026, 11, 20));
    }

    private static AlertaActiva soat(String estado, LocalDate objetivo) {
        return new AlertaActiva(10L, 2L, 3L, "SOAT", estado, "XYZ999", "SOAT", objetivo);
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
    void vencidaRecordatorioDiarioSinRafaga() {
        assertEquals(Motivo.ENTRADA_ESTADO, svc.evaluar(alerta("VENCIDA"), null, hoy).motivo());
        Control ayer = new Control(1L, "VENCIDA", hoy.minusDays(1), "ENTRADA_ESTADO", null);
        assertEquals(Motivo.RECORDATORIO, svc.evaluar(alerta("VENCIDA"), ayer, hoy).motivo());
        Control hoyYa = new Control(1L, "VENCIDA", hoy, "RECORDATORIO", null);
        assertNull(svc.evaluar(alerta("VENCIDA"), hoyYa, hoy));
        Control viejo = new Control(1L, "VENCIDA", hoy.minusDays(10), "RECORDATORIO", null);
        assertEquals(Motivo.RECORDATORIO, svc.evaluar(alerta("VENCIDA"), viejo, hoy).motivo());
    }

    @Test
    void entradaVencidaAunqueHuboPushPendienteHoy() {
        // Coherencia: si el km hace pasar a VENCIDA el mismo día, debe haber push de entrada.
        Control hoyPendiente = new Control(1L, "PENDIENTE", hoy, "ENTRADA_ESTADO", null);
        assertEquals(Motivo.ENTRADA_ESTADO, svc.evaluar(alerta("VENCIDA"), hoyPendiente, hoy).motivo());
        // Recordatorio del mismo estado: sí tope 1/día.
        Control hoyVencida = new Control(1L, "VENCIDA", hoy, "ENTRADA_ESTADO", null);
        assertNull(svc.evaluar(alerta("VENCIDA"), hoyVencida, hoy));
    }

    @Test
    void soatHitosYSemanal() {
        LocalDate venc = LocalDate.of(2026, 11, 20);
        // Entrada PROXIMA
        assertEquals(Motivo.HITO_SOAT,
                svc.evaluarSoat(soat("PROXIMA", venc), null, venc.minusDays(25)).motivo());
        // Día siguiente al vencimiento
        LocalDate diaDespues = venc.plusDays(1);
        assertEquals(Motivo.HITO_SOAT,
                svc.evaluarSoat(soat("VENCIDA", venc), null, diaDespues).motivo());
        Control yaVencida = new Control(10L, "VENCIDA", diaDespues, "HITO_SOAT", null);
        assertNull(svc.evaluarSoat(soat("VENCIDA", venc), yaVencida, diaDespues.plusDays(3)));
        Control hace7 = new Control(10L, "VENCIDA", diaDespues, "HITO_SOAT", null);
        assertEquals(Motivo.RECORDATORIO,
                svc.evaluarSoat(soat("VENCIDA", venc), hace7, diaDespues.plusDays(7)).motivo());
        // 1 día antes
        Control enPendiente = new Control(10L, "PENDIENTE", venc.minusDays(7), "HITO_SOAT", null);
        assertEquals(Motivo.HITO_SOAT,
                svc.evaluarSoat(soat("PENDIENTE", venc), enPendiente, venc.minusDays(1)).motivo());
    }
}
