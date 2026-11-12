package backend.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fallback cuando no hay Firebase Admin SA. */
public class NoOpPushSender implements PushNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(NoOpPushSender.class);

    @Override
    public String proveedor() {
        return "NONE";
    }

    @Override
    public ResultadoEnvio enviar(MensajePush mensaje) {
        log.debug("Push omitido (sin adaptador FCM): destino={}", mensaje.destino());
        return ResultadoEnvio.OMITIDO;
    }
}
