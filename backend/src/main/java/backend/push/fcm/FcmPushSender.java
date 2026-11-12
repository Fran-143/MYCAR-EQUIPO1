package backend.push.fcm;

import backend.push.PushNotificationSender;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FcmPushSender implements PushNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private final FirebaseMessaging messaging;

    public FcmPushSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public String proveedor() {
        return "FCM";
    }

    @Override
    public ResultadoEnvio enviar(MensajePush mensaje) {
        Map<String, String> data = new HashMap<>();
        data.put("destino", mensaje.destino() == null ? "DETALLE_ALERTA" : mensaje.destino());
        if (mensaje.idAlerta() != null) {
            data.put("id_alerta", String.valueOf(mensaje.idAlerta()));
        }
        if (mensaje.idNotificacion() != null) {
            data.put("id_notificacion", String.valueOf(mensaje.idNotificacion()));
        }
        if (mensaje.idVehiculo() != null) {
            data.put("id_vehiculo", String.valueOf(mensaje.idVehiculo()));
        }
        if (mensaje.categoria() != null) {
            data.put("categoria", mensaje.categoria());
        }
        Message msg = Message.builder()
                .setToken(mensaje.token())
                .setNotification(Notification.builder()
                        .setTitle(mensaje.titulo())
                        .setBody(mensaje.cuerpo())
                        .build())
                .putAllData(data)
                .build();
        try {
            messaging.send(msg);
            return ResultadoEnvio.ENVIADO;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED
                    || code == MessagingErrorCode.INVALID_ARGUMENT) {
                log.info("FCM token inválido: {}", code);
                return ResultadoEnvio.TOKEN_INVALIDO;
            }
            log.warn("FCM error temporal: {} — {}", code, e.getMessage());
            return ResultadoEnvio.ERROR_TEMPORAL;
        }
    }
}
