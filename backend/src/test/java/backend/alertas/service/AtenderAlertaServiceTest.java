package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.dao.AlertasJdbcDao.AlertaOperable;
import backend.alertas.model.ErrorAlertas;
import backend.mantenimiento.model.ResultadoRegistroMantenimiento;
import backend.mantenimiento.service.RegistroMantenimientoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AtenderAlertaServiceTest {
    AlertasJdbcDao alertas;
    RegistroMantenimientoService registro;
    AtenderAlertaService servicio;

    @BeforeEach void preparar() {
        alertas = mock(AlertasJdbcDao.class);
        registro = mock(RegistroMantenimientoService.class);
        servicio = new AtenderAlertaService(alertas, registro);
    }

    @Test void atiendeVencida() {
        UUID op = UUID.randomUUID();
        when(alertas.bloquearAlertaPropia(9, 3, 12)).thenReturn(Optional.of(
                new AlertaOperable(12, 3, "VENCIDA", 2, "CAMBIO_ACEITE", null, LocalDate.now(), null)));
        when(registro.registrarResultado(eq(9L), any())).thenReturn(
                new ResultadoRegistroMantenimiento(op, 3L, 99L, "REALIZADO", 25000, LocalDate.now(), null, 0));
        var body = new ContratoRf22.AtenderRequest(op, LocalDate.now(), 25000, "HISTORICO_MANUAL",
                null, BigDecimal.ONE, "ok", null);
        var r = servicio.atender(9, 3, 12, body);
        assertEquals("REALIZADA", r.estadoAlerta());
        assertEquals(99L, r.idMantenimientoAtencion());
        verify(alertas).marcarRealizada(12, "VENCIDA", 2, 99L, "ATENCION_TARDIA_RF24");
    }

    @Test void rechazaProgramadaParaAtender() {
        when(alertas.bloquearAlertaPropia(9, 3, 12)).thenReturn(Optional.of(
                new AlertaOperable(12, 3, "PROGRAMADA", 0, "CAMBIO_ACEITE", null, null, null)));
        var body = new ContratoRf22.AtenderRequest(UUID.randomUUID(), LocalDate.now(), 1, "HISTORICO_MANUAL",
                null, null, null, null);
        var ex = assertThrows(ErrorAlertas.class, () -> servicio.atender(9, 3, 12, body));
        assertEquals("ALERTA_NO_ATENDIBLE", ex.codigo());
    }

    @Test void anulaManualSinTocarReferencia() {
        when(alertas.bloquearAlertaPropia(9, 3, 12)).thenReturn(Optional.of(
                new AlertaOperable(12, 3, "PROXIMA", 1, "CAMBIO_ACEITE", null, null, null)));
        var r = servicio.anular(9, 3, 12);
        assertEquals("ANULADA", r.estado());
        verify(alertas).marcarAnuladaManual(12, "PROXIMA", 1);
        verifyNoInteractions(registro);
    }
}
