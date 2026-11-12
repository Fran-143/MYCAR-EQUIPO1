package backend.alertas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.dao.AlertasJdbcDao.VehiculoActivo;
import backend.alertas.service.EvaluacionProgramadaAlertasService.Modo;
import backend.notificaciones.service.NotificacionInternaService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class EvaluacionProgramadaAlertasServiceTest {

    @Test
    void errorEnUnVehiculoNoDetieneElLote() {
        AlertasJdbcDao dao = mock(AlertasJdbcDao.class);
        when(dao.listarVehiculosActivosConAlertasEvaluables())
                .thenReturn(List.of(new VehiculoActivo(1L, 10L), new VehiculoActivo(2L, 20L)));
        when(dao.sincronizar(1L, 10L)).thenThrow(new RuntimeException("fallo puntual"));
        when(dao.sincronizar(2L, 20L)).thenReturn(3);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificacionInternaService> n = mock(ObjectProvider.class);
        when(n.getIfAvailable()).thenReturn(null);

        var r = new EvaluacionProgramadaAlertasService(dao, n).evaluar(Modo.COMPLETO);

        assertThat(r.omitidoEnCurso()).isFalse();
        assertThat(r.vehiculos()).isEqualTo(2);
        assertThat(r.cambiosEstado()).isEqualTo(3);
        assertThat(r.erroresVehiculo()).isEqualTo(1);
        verify(dao).sincronizar(1L, 10L);
        verify(dao).sincronizar(2L, 20L);
    }

    @Test
    void simularNoEscribe() {
        AlertasJdbcDao dao = mock(AlertasJdbcDao.class);
        when(dao.listarVehiculosActivosConAlertasEvaluables())
                .thenReturn(List.of(new VehiculoActivo(1L, 10L)));
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificacionInternaService> n = mock(ObjectProvider.class);

        var r = new EvaluacionProgramadaAlertasService(dao, n).evaluar(Modo.SIMULAR);

        assertThat(r.modo()).isEqualTo("SIMULAR");
        assertThat(r.vehiculos()).isEqualTo(1);
        assertThat(r.cambiosEstado()).isEqualTo(0);
        verifyNoInteractions(n);
        verify(dao).listarVehiculosActivosConAlertasEvaluables();
    }

    @Test
    void concurrenciaOmiteSegundaEjecucion() throws Exception {
        AlertasJdbcDao dao = mock(AlertasJdbcDao.class);
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        when(dao.listarVehiculosActivosConAlertasEvaluables()).thenAnswer(inv -> {
            dentro.countDown();
            liberar.await(5, TimeUnit.SECONDS);
            return List.of();
        });
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificacionInternaService> n = mock(ObjectProvider.class);
        when(n.getIfAvailable()).thenReturn(null);
        var servicio = new EvaluacionProgramadaAlertasService(dao, n);

        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<EvaluacionProgramadaAlertasService.Resultado> a =
                    pool.submit(() -> servicio.evaluar(Modo.COMPLETO));
            assertThat(dentro.await(5, TimeUnit.SECONDS)).isTrue();
            var b = servicio.evaluar(Modo.COMPLETO);
            assertThat(b.omitidoEnCurso()).isTrue();
            liberar.countDown();
            assertThat(a.get(5, TimeUnit.SECONDS).omitidoEnCurso()).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void jobDelegaEnServicio() {
        EvaluacionProgramadaAlertasService servicio = mock(EvaluacionProgramadaAlertasService.class);
        when(servicio.evaluar(Modo.COMPLETO))
                .thenReturn(new EvaluacionProgramadaAlertasService.Resultado(false, "COMPLETO", 0, 0, 0));
        new EvaluacionDiariaAlertasJob(servicio).evaluarDiario();
        verify(servicio).evaluar(Modo.COMPLETO);
    }
}
