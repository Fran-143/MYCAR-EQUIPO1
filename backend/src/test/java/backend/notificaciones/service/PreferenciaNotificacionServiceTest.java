package backend.notificaciones.service;

import backend.notificaciones.api.dto.ContratoPreferenciasNotificacion;
import backend.notificaciones.dao.PreferenciaNotificacionJdbcDao;
import backend.notificaciones.model.ErrorNotificaciones;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PreferenciaNotificacionServiceTest {
    PreferenciaNotificacionJdbcDao dao;
    PreferenciaNotificacionService servicio;

    @BeforeEach
    void preparar() {
        dao = mock(PreferenciaNotificacionJdbcDao.class);
        servicio = new PreferenciaNotificacionService(dao);
    }

    @Test
    void consultarAseguraFila() {
        var pref = new ContratoPreferenciasNotificacion.PreferenciasResponse(
                true, true, true, "NO_SOLICITADO");
        when(dao.consultar(12L)).thenReturn(Optional.of(pref));
        var r = servicio.consultar(12L);
        verify(dao).asegurarFila(12L);
        assertTrue(r.mantenimiento());
        assertEquals("NO_SOLICITADO", r.permisoDispositivo());
    }

    @Test
    void guardarTodasOffSinExigirPermisoEspecial() {
        var guardado = new ContratoPreferenciasNotificacion.PreferenciasResponse(
                false, false, false, "NO_SOLICITADO");
        when(dao.actualizar(12L, false, false, false, "NO_SOLICITADO")).thenReturn(guardado);
        var req = new ContratoPreferenciasNotificacion.PreferenciasRequest(
                false, false, false, null);
        var r = servicio.guardar(12L, req);
        assertFalse(r.mantenimiento());
        assertFalse(r.soat());
        assertFalse(r.kilometraje());
        verify(dao).actualizar(12L, false, false, false, "NO_SOLICITADO");
    }

    @Test
    void guardarConPermisoDenegado() {
        var guardado = new ContratoPreferenciasNotificacion.PreferenciasResponse(
                true, false, false, "DENEGADO");
        when(dao.actualizar(12L, true, false, false, "DENEGADO")).thenReturn(guardado);
        var req = new ContratoPreferenciasNotificacion.PreferenciasRequest(
                true, false, false, "DENEGADO");
        assertEquals("DENEGADO", servicio.guardar(12L, req).permisoDispositivo());
    }

    @Test
    void guardarRechazaCamposNulos() {
        var req = new ContratoPreferenciasNotificacion.PreferenciasRequest(
                true, null, false, "CONCEDIDO");
        var ex = assertThrows(ErrorNotificaciones.class, () -> servicio.guardar(12L, req));
        assertEquals("DATOS_INVALIDOS", ex.codigo());
        verify(dao, never()).actualizar(anyLong(), anyBoolean(), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    void permisoInvalido() {
        var ex = assertThrows(ErrorNotificaciones.class,
                () -> servicio.actualizarPermisoDispositivo(12L, "TALVEZ"));
        assertEquals("DATOS_INVALIDOS", ex.codigo());
    }

    @Test
    void actualizarSoloPermisoEa04() {
        var r = new ContratoPreferenciasNotificacion.PreferenciasResponse(
                true, true, false, "DENEGADO");
        when(dao.actualizarSoloPermiso(12L, "DENEGADO")).thenReturn(r);
        assertEquals("DENEGADO", servicio.actualizarPermisoDispositivo(12L, "denegado").permisoDispositivo());
        verify(dao).actualizarSoloPermiso(12L, "DENEGADO");
    }
}
