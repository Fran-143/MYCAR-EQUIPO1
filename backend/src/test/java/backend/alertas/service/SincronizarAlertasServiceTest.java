package backend.alertas.service;

import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.model.ErrorAlertas;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SincronizarAlertasServiceTest {
    AlertasJdbcDao dao;
    SincronizarAlertasService servicio;

    @BeforeEach void preparar() {
        dao = mock(AlertasJdbcDao.class);
        servicio = new SincronizarAlertasService(dao);
    }

    @Test void listarSincronizaYDevuelveLista() {
        when(dao.sincronizar(3L, 9L)).thenReturn(2);
        when(dao.listar(3L)).thenReturn(List.of());
        var r = servicio.listarMisAlertas(9L, 3L);
        assertEquals(2, r.alertasSincronizadas());
        verify(dao).exigirVehiculoPropioActivo(9L, 3L);
        verify(dao).sincronizar(3L, 9L);
    }

    @Test void vehiculoAjenoEs404() {
        doThrow(new EmptyResultDataAccessException(1)).when(dao).exigirVehiculoPropioActivo(9L, 3L);
        var ex = assertThrows(ErrorAlertas.class, () -> servicio.listarMisAlertas(9L, 3L));
        assertEquals("VEHICULO_NO_ENCONTRADO", ex.codigo());
        verify(dao, never()).sincronizar(anyLong(), anyLong());
    }

    @Test void vehiculoInvalidoSinConsultarDao() {
        var ex = assertThrows(ErrorAlertas.class, () -> servicio.listarMisAlertas(9L, 0L));
        assertEquals("VEHICULO_NO_ENCONTRADO", ex.codigo());
        verifyNoInteractions(dao);
    }
}
