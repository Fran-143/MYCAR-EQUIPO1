package backend.push.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.notificaciones.api.dto.ContratoPreferenciasNotificacion;
import backend.notificaciones.dao.PreferenciaNotificacionJdbcDao;
import backend.push.PushNotificationSender;
import backend.push.PushNotificationSender.MensajePush;
import backend.push.PushNotificationSender.ResultadoEnvio;
import backend.push.dao.AlertaPushControlJdbcDao;
import backend.push.dao.DispositivoPushJdbcDao;
import backend.push.dao.OutboxPushJdbcDao;
import backend.push.horario.VentanaPushLima;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

class OutboxPushServiceTest {
    OutboxPushJdbcDao outbox;
    DispositivoPushJdbcDao dispositivos;
    PushNotificationSender sender;
    PreferenciaNotificacionJdbcDao preferencias;
    AlertaPushControlJdbcDao control;
    OutboxPushService servicio;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        outbox = mock(OutboxPushJdbcDao.class);
        dispositivos = mock(DispositivoPushJdbcDao.class);
        sender = mock(PushNotificationSender.class);
        preferencias = mock(PreferenciaNotificacionJdbcDao.class);
        control = mock(AlertaPushControlJdbcDao.class);
        ObjectProvider<AlertaPushControlJdbcDao> ctrlProv = mock(ObjectProvider.class);
        when(ctrlProv.getIfAvailable()).thenReturn(control);
        when(sender.proveedor()).thenReturn("FCM");
        when(preferencias.consultar(anyLong())).thenReturn(Optional.of(
                new ContratoPreferenciasNotificacion.PreferenciasResponse(
                        true, true, true, "CONCEDIDO")));
        servicio = new OutboxPushService(
                outbox, dispositivos, sender, mock(JdbcTemplate.class), preferencias,
                ctrlProv, new VentanaPushLima(), false);
    }

    @Test
    void procesarEnviaYMarcaEnviadoYControl() {
        when(outbox.reclamarPendientes(10)).thenReturn(List.of(
                new OutboxPushJdbcDao.OutboxFila(
                        1L, 12L, 5L, 99L, "MANTENIMIENTO", "T", "C", "DETALLE_ALERTA", 0,
                        "MANT:5:2026-11-12:ENTRADA_ESTADO")));
        when(outbox.alertasDeOutbox(1L)).thenReturn(List.of(5L));
        when(dispositivos.listarActivos(12L, "FCM")).thenReturn(List.of(
                new DispositivoPushJdbcDao.TokenActivo("tok", UUID.randomUUID())));
        when(sender.enviar(any(MensajePush.class))).thenReturn(ResultadoEnvio.ENVIADO);

        assertEquals(1, servicio.procesarLote(10));
        verify(outbox).marcar(1L, "ENVIADO", null);
        verify(control).upsertEnvio(eq(5L), anyString(), any(), eq("ENTRADA_ESTADO"));
    }

    @Test
    void errorTemporalNoActualizaControl() {
        when(outbox.reclamarPendientes(anyInt())).thenReturn(List.of(
                new OutboxPushJdbcDao.OutboxFila(
                        2L, 12L, 5L, 99L, "MANTENIMIENTO", "T", "C", "DETALLE_ALERTA", 0,
                        "MANT:5:2026-11-12:ENTRADA_ESTADO")));
        when(dispositivos.listarActivos(12L, "FCM")).thenReturn(List.of(
                new DispositivoPushJdbcDao.TokenActivo("tok", UUID.randomUUID())));
        when(sender.enviar(any(MensajePush.class))).thenReturn(ResultadoEnvio.ERROR_TEMPORAL);

        assertEquals(0, servicio.procesarLote(5));
        verify(outbox).marcar(2L, "ERROR", "temporal");
        verify(control, never()).upsertEnvio(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void sinProveedorNoProcesa() {
        when(sender.proveedor()).thenReturn("NONE");
        assertEquals(0, servicio.procesarLote(5));
        verify(outbox, never()).reclamarPendientes(anyInt());
    }
}
