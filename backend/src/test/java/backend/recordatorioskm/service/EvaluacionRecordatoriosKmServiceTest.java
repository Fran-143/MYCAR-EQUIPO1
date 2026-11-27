package backend.recordatorioskm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.recordatorioskm.dao.RecordatorioKmJdbcDao;
import backend.recordatorioskm.dao.RecordatorioKmJdbcDao.PendienteJobRow;
import backend.recordatorioskm.domain.FrecuenciaRecordatorioKm;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluacionRecordatoriosKmServiceTest {

    @Mock
    RecordatorioKmJdbcDao dao;

    @InjectMocks
    EvaluacionRecordatoriosKmService servicio;

    @Test
    void emiteYProgramaSiguienteSegunFrecuenciaSelect() {
        Instant proximo = LocalDate.of(2026, 11, 26)
                .atStartOfDay(ZoneId.of("America/Lima")).toInstant();
        when(dao.listarPendientesJob(any())).thenReturn(List.of(
                new PendienteJobRow(1L, 10L, "Toyota", "Corolla", "SEMANAL", proximo)));
        when(dao.insertarNotificacionKm(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        assertEquals(1, servicio.evaluarPendientes());

        verify(dao).insertarNotificacionKm(
                eq(1L), eq(10L), anyString(), anyString(), eq("KM:10:2026-11-26:SEMANAL"));
        ArgumentCaptor<Instant> siguiente = ArgumentCaptor.forClass(Instant.class);
        verify(dao).marcarEnvioYProgramarSiguiente(eq(10L), any(), siguiente.capture());
        LocalDate prog = siguiente.getValue().atZone(ZoneId.of("America/Lima")).toLocalDate();
        assertTrue(prog.isAfter(LocalDate.now(ZoneId.of("America/Lima"))),
                "el próximo envío debe quedar en el futuro según SEMANAL");
    }

    @Test
    void aunqueNotifYaExistaAvanzaCalendarioConFrecuencia() {
        Instant proximo = LocalDate.of(2026, 11, 26)
                .atStartOfDay(ZoneId.of("America/Lima")).toInstant();
        when(dao.listarPendientesJob(any())).thenReturn(List.of(
                new PendienteJobRow(1L, 10L, "Toyota", "Corolla", "DIARIO", proximo)));
        when(dao.insertarNotificacionKm(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(false);

        assertEquals(0, servicio.evaluarPendientes());
        verify(dao, times(1)).marcarEnvioYProgramarSiguiente(eq(10L), any(), any());
    }

    @Test
    void noEmiteSinPendientes() {
        when(dao.listarPendientesJob(any())).thenReturn(List.of());
        assertEquals(0, servicio.evaluarPendientes());
        verify(dao, never()).insertarNotificacionKm(anyLong(), anyLong(), anyString(), anyString(), anyString());
        verify(dao, never()).marcarEnvioYProgramarSiguiente(anyLong(), any(), any());
    }

    @Test
    void siguienteDiarioEsMananaLima() {
        LocalDate hoy = LocalDate.of(2026, 3, 10);
        Instant s = EvaluacionRecordatoriosKmService.siguienteEnvioSegunFrecuencia(
                FrecuenciaRecordatorioKm.DIARIO, hoy);
        assertEquals(LocalDate.of(2026, 3, 11), s.atZone(ZoneId.of("America/Lima")).toLocalDate());
    }

    @Test
    void siguienteSemanalSumaSiete() {
        LocalDate hoy = LocalDate.of(2026, 3, 10);
        Instant s = EvaluacionRecordatoriosKmService.siguienteEnvioSegunFrecuencia(
                FrecuenciaRecordatorioKm.SEMANAL, hoy);
        assertEquals(LocalDate.of(2026, 3, 17), s.atZone(ZoneId.of("America/Lima")).toLocalDate());
    }
}
