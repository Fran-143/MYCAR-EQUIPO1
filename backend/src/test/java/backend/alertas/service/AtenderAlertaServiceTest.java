package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.dao.AlertasJdbcDao.AlertaOperable;
import backend.alertas.dao.OperacionAlertaJdbcDao;
import backend.alertas.model.ErrorAlertas;
import backend.mantenimiento.model.ResultadoRegistroMantenimiento;
import backend.mantenimiento.service.RegistroMantenimientoService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    OperacionAlertaJdbcDao operaciones;
    AtenderAlertaService servicio;

    @BeforeEach void preparar() {
        alertas = mock(AlertasJdbcDao.class);
        registro = mock(RegistroMantenimientoService.class);
        operaciones = mock(OperacionAlertaJdbcDao.class);
        servicio = new AtenderAlertaService(alertas, registro, operaciones, new ObjectMapper());
    }

    @Test void atiendeVencida() {
        UUID op = UUID.randomUUID();
        when(alertas.bloquearAlertaPropia(9, 3, 12)).thenReturn(Optional.of(
                new AlertaOperable(12, 3, "VENCIDA", 2, "CAMBIO_ACEITE", null, LocalDate.now(), null, null)));
        when(registro.registrarResultado(eq(9L), any())).thenReturn(
                new ResultadoRegistroMantenimiento(op, 3L, 99L, "REALIZADO", 25000, LocalDate.now(), null, 0));
        var body = new ContratoRf22.AtenderRequest(op, LocalDate.now(), 25000, "HISTORICO_MANUAL",
                null, BigDecimal.ONE, "ok", null);
        var r = servicio.atender(9, 3, 12, body);
        assertEquals("REALIZADA", r.estadoAlerta());
        assertEquals(99L, r.idMantenimientoAtencion());
        verify(alertas).marcarRealizada(12, "VENCIDA", 2, 99L, "ATENCION_TARDIA_RF24");
    }

    @Test void idempotenteSiYaRealizada() {
        UUID op = UUID.randomUUID();
        when(alertas.bloquearAlertaPropia(9, 3, 12)).thenReturn(Optional.of(
                new AlertaOperable(12, 3, "REALIZADA", 3, "CAMBIO_ACEITE", null, null, null, 99L)));
        var body = new ContratoRf22.AtenderRequest(op, LocalDate.now(), 25000, "HISTORICO_MANUAL",
                null, null, null, null);
        var r = servicio.atender(9, 3, 12, body);
        assertEquals(99L, r.idMantenimientoAtencion());
        verifyNoInteractions(registro);
        verify(alertas, never()).marcarRealizada(anyLong(), any(), anyInt(), anyLong(), any());
    }

    @Test void rechazaProgramadaParaAtender() {
        when(alertas.bloquearAlertaPropia(9, 3, 12)).thenReturn(Optional.of(
                new AlertaOperable(12, 3, "PROGRAMADA", 0, "CAMBIO_ACEITE", null, null, null, null)));
        var body = new ContratoRf22.AtenderRequest(UUID.randomUUID(), LocalDate.now(), 1, "HISTORICO_MANUAL",
                null, null, null, null);
        var ex = assertThrows(ErrorAlertas.class, () -> servicio.atender(9, 3, 12, body));
        assertEquals("ALERTA_NO_ATENDIBLE", ex.codigo());
    }

    @Test void anulaManualSinTocarReferencia() {
        UUID op = UUID.randomUUID();
        String motivo = "Ya no aplica";
        String huella = HuellaAnularAlerta.calcular(3, 12, motivo, op);
        when(operaciones.intentarCrear(eq(op), eq(9L), eq("ANULAR_ALERTA"), eq(huella))).thenReturn(true);
        when(operaciones.buscarYBloquear(op, "ANULAR_ALERTA")).thenReturn(Optional.of(
                new OperacionAlertaJdbcDao.OperacionAlerta(op, 9L, huella, "EN_PROCESO", null)));
        when(alertas.bloquearAlertaPropia(9, 3, 12)).thenReturn(Optional.of(
                new AlertaOperable(12, 3, "PROXIMA", 1, "CAMBIO_ACEITE", null, null, null, null)));
        var r = servicio.anular(9, 3, 12, new ContratoRf22.AnularRequest(op, motivo));
        assertEquals("ANULADA", r.estado());
        assertEquals(op, r.idOperacion());
        verify(alertas).marcarAnuladaManual(12, "PROXIMA", 1, motivo);
        verify(operaciones).confirmar(op, 9L, 12L, "ANULADA", "ANULAR_ALERTA");
        verifyNoInteractions(registro);
    }

    @Test void pasaIdTallerARegistroRf17() {
        UUID op = UUID.randomUUID();
        when(alertas.bloquearAlertaPropia(9, 3, 12)).thenReturn(Optional.of(
                new AlertaOperable(12, 3, "PROXIMA", 1, "CAMBIO_ACEITE", null, null, null, null)));
        when(registro.registrarResultado(eq(9L), any())).thenReturn(
                new ResultadoRegistroMantenimiento(op, 3L, 88L, "REALIZADO", 20000, LocalDate.now(), null, 0));
        var body = new ContratoRf22.AtenderRequest(op, LocalDate.now(), 20000, "HISTORICO_MANUAL",
                null, null, null, null, 55L);
        servicio.atender(9, 3, 12, body);
        verify(registro).registrarResultado(eq(9L), argThat(s ->
                Long.valueOf(55L).equals(s.idTaller())
                        && Long.valueOf(55L).equals(s.datos().idTaller())));
        verify(alertas).marcarRealizada(12, "PROXIMA", 1, 88L, "ATENCION_RF24");
    }

    @Test void anularIdempotenteSiYaAnuladaMismaOperacionConfirmada() throws Exception {
        UUID op = UUID.randomUUID();
        String motivo = "Duplicado";
        String huella = HuellaAnularAlerta.calcular(3, 12, motivo, op);
        String payload = new ObjectMapper().writeValueAsString(java.util.Map.of("idAlerta", 12, "estado", "ANULADA"));
        when(operaciones.intentarCrear(eq(op), eq(9L), eq("ANULAR_ALERTA"), eq(huella))).thenReturn(false);
        when(operaciones.buscarYBloquear(op, "ANULAR_ALERTA")).thenReturn(Optional.of(
                new OperacionAlertaJdbcDao.OperacionAlerta(op, 9L, huella, "CONFIRMADA", payload)));
        var r = servicio.anular(9, 3, 12, new ContratoRf22.AnularRequest(op, motivo));
        assertEquals("ANULADA", r.estado());
        verify(alertas, never()).bloquearAlertaPropia(anyLong(), anyLong(), anyLong());
        verify(alertas, never()).marcarAnuladaManual(anyLong(), any(), anyInt(), any());
    }

    @Test void anularRechazaMotivoVacio() {
        var ex = assertThrows(ErrorAlertas.class, () ->
                servicio.anular(9, 3, 12, new ContratoRf22.AnularRequest(UUID.randomUUID(), "  ")));
        assertEquals("DATOS_INVALIDOS", ex.codigo());
    }
}
