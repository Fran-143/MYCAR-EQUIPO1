package backend.notificaciones.service;

import backend.notificaciones.api.dto.ContratoNotificaciones;
import backend.notificaciones.dao.NotificacionJdbcDao;
import backend.notificaciones.model.ErrorNotificaciones;
import backend.push.dao.AlertaPushControlJdbcDao;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificacionInternaServiceTest {
    NotificacionJdbcDao dao;
    NotificacionInternaService servicio;

    @BeforeEach
    void preparar() {
        dao = mock(NotificacionJdbcDao.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AlertaPushControlJdbcDao> pushControl = mock(ObjectProvider.class);
        when(pushControl.getIfAvailable()).thenReturn(null);
        servicio = new NotificacionInternaService(dao, pushControl);
    }

    @Test
    void programadaNoCreaAvisoActivo() {
        when(dao.cargarAlerta(1L)).thenReturn(Optional.of(new NotificacionJdbcDao.AlertaContexto(
                1L, 7L, 12L, "PROGRAMADA", "Aceite", "AVB456", "Aceite")));
        servicio.crearOActualizarSegunEstadoAlerta(1L);
        verify(dao, never()).upsertPorAlerta(anyLong(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void proximaCreaPendienteAunquePreferenciaExternaOff() {
        when(dao.cargarAlerta(2L)).thenReturn(Optional.of(new NotificacionJdbcDao.AlertaContexto(
                2L, 7L, 12L, "PROXIMA", "Aceite", "AVB456", "Aceite")));
        servicio.crearOActualizarSegunEstadoAlerta(2L);
        verify(dao).upsertPorAlerta(eq(12L), eq(2L), eq(7L), eq("PENDIENTE"), anyString(), anyString());
    }

    @Test
    void vencidaSiguePendiente() {
        when(dao.cargarAlerta(3L)).thenReturn(Optional.of(new NotificacionJdbcDao.AlertaContexto(
                3L, 7L, 12L, "VENCIDA", "Aceite", "AVB456", "Aceite")));
        servicio.crearOActualizarSegunEstadoAlerta(3L);
        verify(dao).upsertPorAlerta(eq(12L), eq(3L), eq(7L), eq("PENDIENTE"), contains("vencido"), anyString());
    }

    @Test
    void vencidaReevaluadaNoUsaOtroCamino() {
        when(dao.cargarAlerta(3L)).thenReturn(Optional.of(new NotificacionJdbcDao.AlertaContexto(
                3L, 7L, 12L, "VENCIDA", "Aceite", "AVB456", "Aceite")));
        servicio.crearOActualizarSegunEstadoAlerta(3L);
        servicio.crearOActualizarSegunEstadoAlerta(3L);
        verify(dao, times(2)).upsertPorAlerta(eq(12L), eq(3L), eq(7L), eq("PENDIENTE"), contains("vencido"), anyString());
        verify(dao, never()).marcarGestionSiActiva(anyLong(), anyString());
    }

    @Test
    void realizadaMarcaAtendida() {
        when(dao.cargarAlerta(4L)).thenReturn(Optional.of(new NotificacionJdbcDao.AlertaContexto(
                4L, 7L, 12L, "REALIZADA", "Aceite", "AVB456", "Aceite")));
        servicio.crearOActualizarSegunEstadoAlerta(4L);
        verify(dao).marcarGestionSiActiva(4L, "ATENDIDA");
        verify(dao, never()).upsertPorAlerta(anyLong(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void anularDesactivaPendientes() {
        servicio.desactivarAvisosPendientesAlertaAnulada(9L);
        verify(dao).desactivarPendientesDeAlerta(9L);
    }

    @Test
    void marcarLeidaNoCambiaGestion() {
        var dto = new ContratoNotificaciones.NotificacionDto(
                5L, "MANTENIMIENTO", "PENDIENTE", false, "t", "c", "DETALLE_ALERTA",
                9L, 7L, "2026-11-08T00:00:00.000Z");
        var leida = new ContratoNotificaciones.NotificacionDto(
                5L, "MANTENIMIENTO", "PENDIENTE", true, "t", "c", "DETALLE_ALERTA",
                9L, 7L, "2026-11-08T00:00:00.000Z");
        when(dao.obtenerPropia(12L, 5L)).thenReturn(Optional.of(dto), Optional.of(leida));
        when(dao.marcarLeida(12L, 5L)).thenReturn(1);
        var r = servicio.marcarLeida(12L, 5L);
        assertTrue(r.leida());
        assertEquals("PENDIENTE", r.estadoGestion());
        verify(dao, never()).marcarGestionSiActiva(anyLong(), anyString());
    }

    @Test
    void bandejaRechazaCategoriaInvalida() {
        assertThrows(ErrorNotificaciones.class,
                () -> servicio.consultarBandeja(12L, "PENDIENTES", "FOO"));
    }

    @Test
    void bandejaRechazaFiltroLecturaInvalido() {
        assertThrows(ErrorNotificaciones.class,
                () -> servicio.consultarBandeja(12L, "PENDIENTES", "TODAS", "XYZ"));
    }

    @Test
    void bandejaDevuelveContadoresIndependientes() {
        when(dao.conciliarVigencia(12L)).thenReturn(0);
        when(dao.contarPendientes(12L)).thenReturn(2);
        when(dao.contarNoLeidas(12L)).thenReturn(1);
        when(dao.listar(12L, "PENDIENTES", "SOAT", "NO_LEIDAS")).thenReturn(List.of());
        var r = servicio.consultarBandeja(12L, "PENDIENTES", "SOAT", "NO_LEIDAS");
        assertEquals(2, r.contadorPendientesGlobal());
        assertEquals(1, r.contadorNoLeidasGlobal());
        assertEquals("NO_LEIDAS", r.filtroLectura());
        assertEquals("SOAT", r.categoria());
        assertTrue(r.notificaciones().isEmpty());
        verify(dao).listar(12L, "PENDIENTES", "SOAT", "NO_LEIDAS");
    }

    @Test
    void bandejaCompatSinFiltroLecturaUsaTodas() {
        when(dao.conciliarVigencia(12L)).thenReturn(0);
        when(dao.contarPendientes(12L)).thenReturn(3);
        when(dao.contarNoLeidas(12L)).thenReturn(2);
        when(dao.listar(12L, "PENDIENTES", "TODAS", "TODAS")).thenReturn(List.of());
        var r = servicio.consultarBandeja(12L, "PENDIENTES", "TODAS");
        assertEquals("TODAS", r.filtroLectura());
        assertEquals(2, r.contadorNoLeidasGlobal());
    }

    @Test
    void ajenaNoEncontrada() {
        when(dao.obtenerPropia(12L, 99L)).thenReturn(Optional.empty());
        var ex = assertThrows(ErrorNotificaciones.class, () -> servicio.marcarLeida(12L, 99L));
        assertEquals("NOTIFICACION_NO_ENCONTRADA", ex.codigo());
    }
}
