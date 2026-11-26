package backend.push;

/** Puerto de envío push — independiente de RF22–24 / RF46–48. FCM es un adaptador. */
public interface PushNotificationSender {
    String proveedor();

    ResultadoEnvio enviar(MensajePush mensaje);

    record MensajePush(
            String token,
            String titulo,
            String cuerpo,
            String destino,
            Long idAlerta,
            Long idNotificacion,
            Long idVehiculo,
            String categoria) {
    }

    enum ResultadoEnvio {
        ENVIADO,
        TOKEN_INVALIDO,
        ERROR_TEMPORAL,
        OMITIDO
    }
}
