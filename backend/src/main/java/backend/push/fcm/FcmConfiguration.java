package backend.push.fcm;

import backend.push.PushNotificationSender;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Único proveedor de {@link PushNotificationSender} cuando {@code mycar.fcm.habilitado=true}.
 * Exige credenciales SA; no registra NoOp (ese bean solo existe con habilitado=false).
 * No usa Firestore ni Realtime Database.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mycar.fcm", name = "habilitado", havingValue = "true")
public class FcmConfiguration {
    private static final Logger log = LoggerFactory.getLogger(FcmConfiguration.class);

    /**
     * Lazy: Firebase Admin / OAuth no deben bloquear la apertura de Tomcat en Render Free.
     * El primer envío o tick del worker materializa el SDK.
     */
    @Bean
    @Lazy
    PushNotificationSender pushNotificationSender(
            @Value("${mycar.fcm.credentials-json:}") String jsonInline,
            @Value("${mycar.fcm.credentials-base64:}") String base64,
            @Value("${mycar.fcm.credentials-path:}") String path) throws IOException {
        byte[] bytes;
        try {
            bytes = FcmCredentials.resolver(jsonInline, base64, path);
        } catch (IllegalArgumentException | IOException e) {
            throw new IllegalStateException(
                    "FCM: credenciales SA inválidas o ilegibles (revisar MYCAR_FCM_CREDENTIALS_*). "
                            + "Detalle omitido por seguridad.",
                    e);
        }
        if (bytes == null) {
            throw new IllegalStateException(
                    "FCM habilitado sin credenciales SA. "
                            + "Configure MYCAR_FCM_CREDENTIALS_BASE64 (o credentials-json/path), "
                            + "o ponga mycar.fcm.habilitado=false para NoOp de desarrollo.");
        }
        GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(bytes));
        FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
        FirebaseApp app = FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(options)
                : FirebaseApp.getInstance();
        log.info("FCM Admin SDK inicializado (proveedor=FCM)");
        return new FcmPushSender(FirebaseMessaging.getInstance(app));
    }
}
