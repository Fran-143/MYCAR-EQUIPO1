package backend.recordatorioskm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    void noDuplicaMismaClaveDeCiclo() {
        Instant proximo = LocalDate.of(2026, 11, 26)
                .atStartOfDay(ZoneId.of("America/Lima")).toInstant();
        when(dao.listarPendientesJob(any())).thenReturn(List.of(
                new PendienteJobRow(1L, 10L, "Toyota", "Corolla", "SEMANAL", proximo)));
        when(dao.insertarNotificacionKm(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(true, false);

        assertEquals(1, servicio.evaluarPendientes());
        assertEquals(0, servicio.evaluarPendientes());

        verify(dao, times(2)).insertarNotificacionKm(
                eq(1L), eq(10L), anyString(), anyString(), eq("KM:10:2026-11-26:SEMANAL"));
        verify(dao, times(1)).marcarUltimoEnvio(eq(10L), any());
    }

    @Test
    void noEmiteSinPendientes() {
        when(dao.listarPendientesJob(any())).thenReturn(List.of());
        assertEquals(0, servicio.evaluarPendientes());
        verify(dao, never()).insertarNotificacionKm(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }
}
