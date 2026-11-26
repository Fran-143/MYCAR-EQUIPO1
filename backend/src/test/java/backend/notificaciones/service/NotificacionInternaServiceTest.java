package backend.notificaciones.service;

import backend.notificaciones.api.dto.ContratoNotificaciones;
import backend.notificaciones.dao.NotificacionJdbcDao;
import backend.push.dao.AlertaPushControlJdbcDao;
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
        @SuppressWarnings("unchecked")
        ObjectProvider<backend.alertas.dao.AlertasJdbcDao> alertas = mock(ObjectProvider.class);
        when(pushControl.getIfAvailable()).thenReturn(null);
        when(alertas.getIfAvailable()).thenReturn(null);
        servicio = new NotificacionInternaService(dao, pushControl, alertas);
    }

    private static NotificacionJdbcDao.AlertaContexto ctx(long id, String estado) {
        return new NotificacionJdbcDao.AlertaContexto(
                id, 7L, 12L, estado, "MANTENIMIENTO", "AVB456", "Aceite");
    }

    @Test
    void programadaNoCreaAvisoActivo() {
        when(dao.cargarAlerta(1L)).thenReturn(Optional.of(ctx(1L, "PROGRAMADA")));
        servicio.crearOActualizarSegunEstadoAlerta(1L);
        verify(dao, never()).upsertPorAlerta(anyLong(), anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void proximaCreaPendienteAunquePreferenciaExternaOff() {
        when(dao.cargarAlerta(2L)).thenReturn(Optional.of(ctx(2L, "PROXIMA")));
        servicio.crearOActualizarSegunEstadoAlerta(2L);
        verify(dao).upsertPorAlerta(eq(12L), eq(2L), eq(7L), eq("MANTENIMIENTO"), eq("PENDIENTE"), anyString(), anyString());
    }

    @Test
    void vencidaSiguePendiente() {
        when(dao.cargarAlerta(3L)).thenReturn(Optional.of(ctx(3L, "VENCIDA")));
        servicio.crearOActualizarSegunEstadoAlerta(3L);
        verify(dao).upsertPorAlerta(eq(12L), eq(3L), eq(7L), eq("MANTENIMIENTO"), eq("PENDIENTE"), contains("vencido"), anyString());
    }

    @Test
    void soatProximaUsaCategoriaSoat() {
        when(dao.cargarAlerta(8L)).thenReturn(Optional.of(new NotificacionJdbcDao.AlertaContexto(
                8L, 7L, 12L, "PROXIMA", "SOAT", "AVB456", "SOAT")));
        servicio.crearOActualizarSegunEstadoAlerta(8L);
        verify(dao).upsertPorAlerta(eq(12L), eq(8L), eq(7L), eq("SOAT"), eq("PENDIENTE"),
                contains("SOAT"), anyString());
    }

    @Test
    void vencidaReevaluadaNoUsaOtroCamino() {
        when(dao.cargarAlerta(3L)).thenReturn(Optional.of(ctx(3L, "VENCIDA")));
        servicio.crearOActualizarSegunEstadoAlerta(3L);
        servicio.crearOActualizarSegunEstadoAlerta(3L);
        verify(dao, times(2)).upsertPorAlerta(eq(12L), eq(3L), eq(7L), eq("MANTENIMIENTO"), eq("PENDIENTE"), contains("vencido"), anyString());
        verify(dao, never()).marcarGestionSiActiva(anyLong(), anyString());
    }

    @Test
    void realizadaMarcaAtendida() {
        when(dao.cargarAlerta(4L)).thenReturn(Optional.of(ctx(4L, "REALIZADA")));
        servicio.crearOActualizarSegunEstadoAlerta(4L);
        verify(dao).marcarGestionSiActiva(4L, "ATENDIDA");
        verify(dao, never()).upsertPorAlerta(anyLong(), anyLong(), anyLong(), any(), any(), any(), any());
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
        verify(dao).marcarLeida(12L, 5L);
    }
}
