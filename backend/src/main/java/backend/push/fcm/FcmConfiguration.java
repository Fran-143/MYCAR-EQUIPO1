package backend.push.fcm;

import backend.push.NoOpPushSender;
import backend.push.PushNotificationSender;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Inicializa Firebase Admin solo si hay credenciales SA.
 * Fuentes (prioridad): JSON inline → Base64 → ruta de archivo.
 * Sin SA → {@link NoOpPushSender}. No usa Firestore ni Realtime Database.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mycar.fcm", name = "habilitado", havingValue = "true", matchIfMissing = true)
public class FcmConfiguration {
    private static final Logger log = LoggerFactory.getLogger(FcmConfiguration.class);

    @Bean
    PushNotificationSender pushNotificationSender(
            @Value("${mycar.fcm.credentials-json:}") String jsonInline,
            @Value("${mycar.fcm.credentials-base64:}") String base64,
            @Value("${mycar.fcm.credentials-path:}") String path) throws IOException {
        byte[] bytes = resolverCredenciales(jsonInline, base64, path);
        if (bytes == null) {
            log.warn("FCM: sin credenciales SA — push deshabilitado (set mycar.fcm.credentials-*)");
            return new NoOpPushSender();
        }
        GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(bytes));
        FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
        FirebaseApp app = FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(options)
                : FirebaseApp.getInstance();
        log.info("FCM Admin SDK inicializado");
        return new FcmPushSender(FirebaseMessaging.getInstance(app));
    }

    private static byte[] resolverCredenciales(String jsonInline, String base64, String path)
            throws IOException {
        if (StringUtils.hasText(jsonInline)) {
            return jsonInline.getBytes(StandardCharsets.UTF_8);
        }
        if (StringUtils.hasText(base64)) {
            // Render/env a veces envuelve el valor en comillas; no registrar el secreto.
            String cleaned = base64.trim();
            if (cleaned.length() >= 2
                    && ((cleaned.charAt(0) == '"' && cleaned.charAt(cleaned.length() - 1) == '"')
                    || (cleaned.charAt(0) == '\'' && cleaned.charAt(cleaned.length() - 1) == '\''))) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            }
            return Base64.getDecoder().decode(cleaned.replaceAll("\\s", ""));
        }
        if (StringUtils.hasText(path)) {
            Path p = Path.of(path);
            if (Files.isRegularFile(p)) {
                return Files.readAllBytes(p);
            }
            log.warn("FCM: ruta de credenciales no encontrada: {}", path);
        }
        return null;
    }
}
