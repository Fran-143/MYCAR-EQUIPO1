package backend.push.service;

import backend.push.PushNotificationSender;
import backend.push.PushNotificationSender.MensajePush;
import backend.push.PushNotificationSender.ResultadoEnvio;
import backend.push.api.dto.ContratoPush;
import backend.push.dao.DispositivoPushJdbcDao;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DispositivoPushService {
    private final DispositivoPushJdbcDao dao;
    private final PushNotificationSender sender;

    public DispositivoPushService(DispositivoPushJdbcDao dao, PushNotificationSender sender) {
        this.dao = dao;
        this.sender = sender;
    }

    @Transactional
    public void registrar(long usuario, UUID instalacion, String proveedor, String token) {
        if (instalacion == null) {
            throw new IllegalArgumentException("id_instalacion requerido");
        }
        if (!StringUtils.hasText(token) || token.length() > 512) {
            throw new IllegalArgumentException("token inválido");
        }
        String prov = StringUtils.hasText(proveedor) ? proveedor.trim().toUpperCase() : "FCM";
        if (!"FCM".equals(prov)) {
            throw new IllegalArgumentException("proveedor no soportado");
        }
        dao.upsert(usuario, instalacion, prov, token.trim());
    }

    @Transactional
    public void desvincular(long usuario, UUID instalacion) {
        if (instalacion == null) {
            throw new IllegalArgumentException("id_instalacion requerido");
        }
        dao.desactivarPorInstalacion(usuario, instalacion);
    }

    /**
     * Envía un push de prueba únicamente a los tokens activos del usuario autenticado.
     * No hace broadcast ni usa outbox (verificación FCM controlada).
     */
    @Transactional
    public ContratoPush.ProbarResponse probar(long usuario) {
        if ("NONE".equals(sender.proveedor())) {
            throw new IllegalStateException("FCM no configurado (sin credenciales SA)");
        }
        var tokens = dao.listarActivos(usuario, sender.proveedor());
        int enviados = 0;
        int invalidos = 0;
        int errores = 0;
        for (var t : tokens) {
            ResultadoEnvio r = sender.enviar(new MensajePush(
                    t.token(),
                    "MyCar — prueba FCM",
                    "Notificación de verificación en tu dispositivo.",
                    "NOTIFICACIONES",
                    null,
                    null,
                    null,
                    "MANTENIMIENTO"));
            switch (r) {
                case ENVIADO -> enviados++;
                case TOKEN_INVALIDO -> {
                    invalidos++;
                    dao.desactivarPorToken(sender.proveedor(), t.token());
                }
                case ERROR_TEMPORAL, OMITIDO -> errores++;
            }
        }
        return new ContratoPush.ProbarResponse(enviados > 0, tokens.size(), enviados, invalidos, errores);
    }
}
