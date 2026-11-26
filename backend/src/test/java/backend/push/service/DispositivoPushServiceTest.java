package backend.push.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.notificaciones.api.dto.ContratoPreferenciasNotificacion;
import backend.notificaciones.dao.PreferenciaNotificacionJdbcDao;
import backend.push.PushNotificationSender;
import backend.push.dao.DispositivoPushJdbcDao;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DispositivoPushServiceTest {
    DispositivoPushJdbcDao dao;
    PreferenciaNotificacionJdbcDao preferencias;
    DispositivoPushService servicio;

    @BeforeEach
    void preparar() {
        dao = mock(DispositivoPushJdbcDao.class);
        preferencias = mock(PreferenciaNotificacionJdbcDao.class);
        PushNotificationSender sender = mock(PushNotificationSender.class);
        when(sender.proveedor()).thenReturn("FCM");
        when(preferencias.consultar(12L)).thenReturn(Optional.of(
                new ContratoPreferenciasNotificacion.PreferenciasResponse(true, true, true, "NO_SOLICITADO")));
        servicio = new DispositivoPushService(dao, sender, preferencias);
    }

    @Test
    void registrarUpsertFcm() {
        UUID inst = UUID.fromString("643bf983-8f74-41bb-9488-1fbaaf49065b");
        servicio.registrar(12L, inst, "fcm", "token-abc");
        verify(dao).upsert(eq(12L), eq(inst), eq("FCM"), eq("token-abc"));
        verify(preferencias).actualizarSoloPermiso(eq(12L), eq("CONCEDIDO"));
    }

    @Test
    void registrarRechazaTokenVacio() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar(1L, UUID.randomUUID(), "FCM", "  "));
    }

    @Test
    void desvincularPorInstalacion() {
        UUID inst = UUID.randomUUID();
        servicio.desvincular(9L, inst);
        verify(dao).desactivarPorInstalacion(9L, inst);
    }
}
