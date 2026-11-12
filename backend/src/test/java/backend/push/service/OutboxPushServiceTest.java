package backend.push.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.push.PushNotificationSender;
import backend.push.PushNotificationSender.MensajePush;
import backend.push.PushNotificationSender.ResultadoEnvio;
import backend.push.dao.DispositivoPushJdbcDao;
import backend.push.dao.OutboxPushJdbcDao;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OutboxPushServiceTest {
    OutboxPushJdbcDao outbox;
    DispositivoPushJdbcDao dispositivos;
    PushNotificationSender sender;
    OutboxPushService servicio;

    @BeforeEach
    void preparar() {
        outbox = mock(OutboxPushJdbcDao.class);
        dispositivos = mock(DispositivoPushJdbcDao.class);
        sender = mock(PushNotificationSender.class);
        when(sender.proveedor()).thenReturn("FCM");
        servicio = new OutboxPushService(outbox, dispositivos, sender, mock(JdbcTemplate.class), true);
    }

    @Test
    void procesarEnviaYMarcaEnviado() {
        when(outbox.reclamarPendientes(10)).thenReturn(List.of(
                new OutboxPushJdbcDao.OutboxFila(
                        1L, 12L, 5L, 99L, "MANTENIMIENTO", "T", "C", "DETALLE_ALERTA", 0)));
        when(dispositivos.listarActivos(12L, "FCM")).thenReturn(List.of(
                new DispositivoPushJdbcDao.TokenActivo("tok", UUID.randomUUID())));
        when(sender.enviar(any(MensajePush.class))).thenReturn(ResultadoEnvio.ENVIADO);

        assertEquals(1, servicio.procesarLote(10));
        verify(outbox).marcar(1L, "ENVIADO", null);
    }

    @Test
    void tokenInvalidoSeDesactiva() {
        when(outbox.reclamarPendientes(anyInt())).thenReturn(List.of(
                new OutboxPushJdbcDao.OutboxFila(
                        2L, 12L, 5L, 99L, "MANTENIMIENTO", "T", "C", "DETALLE_ALERTA", 0)));
        when(dispositivos.listarActivos(12L, "FCM")).thenReturn(List.of(
                new DispositivoPushJdbcDao.TokenActivo("tok-malo", UUID.randomUUID())));
        when(sender.enviar(any(MensajePush.class))).thenReturn(ResultadoEnvio.TOKEN_INVALIDO);

        assertEquals(0, servicio.procesarLote(5));
        verify(dispositivos).desactivarPorToken("FCM", "tok-malo");
        verify(outbox).marcar(eq(2L), eq("OMITIDO"), anyString());
    }

    @Test
    void sinProveedorNoProcesa() {
        when(sender.proveedor()).thenReturn("NONE");
        assertEquals(0, servicio.procesarLote(5));
        verify(outbox, never()).reclamarPendientes(anyInt());
    }
}
