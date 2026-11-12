package backend.alertas.service;

import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.dao.AlertasJdbcDao.VehiculoActivo;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class EvaluacionDiariaAlertasJobTest {

    @Test void errorEnUnVehiculoNoDetieneElLote() {
        AlertasJdbcDao dao = mock(AlertasJdbcDao.class);
        when(dao.listarVehiculosActivosConAlertasEvaluables()).thenReturn(List.of(
                new VehiculoActivo(1L, 10L),
                new VehiculoActivo(2L, 20L)));
        when(dao.sincronizar(1L, 10L)).thenThrow(new RuntimeException("fallo puntual"));
        when(dao.sincronizar(2L, 20L)).thenReturn(3);
        new EvaluacionDiariaAlertasJob(dao).evaluarDiario();
        verify(dao).sincronizar(1L, 10L);
        verify(dao).sincronizar(2L, 20L);
    }
}
