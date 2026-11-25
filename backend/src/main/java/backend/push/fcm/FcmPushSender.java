package backend.push.fcm;

import backend.push.PushNotificationSender;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
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
    /** Debe coincidir con MycarPushChannels.ALERTAS_ID en Android. */
    public static final String CANAL_ALERTAS = "mycar_alertas";
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
        data.put("titulo", mensaje.titulo() == null ? "" : mensaje.titulo());
        data.put("cuerpo", mensaje.cuerpo() == null ? "" : mensaje.cuerpo());
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
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(CANAL_ALERTAS)
                                .build())
                        .build())
                .putAllData(data)
                .build();
        try {
            String messageId = messaging.send(msg);
            log.info("FCM aceptó mensaje id={} canal={}", messageId, CANAL_ALERTAS);
            return ResultadoEnvio.ENVIADO;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED
                    || code == MessagingErrorCode.INVALID_ARGUMENT
                    || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
                log.info("FCM token inválido permanente: {}", code);
                return ResultadoEnvio.TOKEN_INVALIDO;
            }
            log.warn("FCM error temporal: {} — {}", code, e.getMessage());
            return ResultadoEnvio.ERROR_TEMPORAL;
        }
    }
}
