package backend.push.config;

import backend.push.NoOpPushSender;
import backend.push.PushNotificationSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * NoOp solo cuando FCM está explícitamente deshabilitado.
 * Mutuamente excluyente con {@code FcmConfiguration} (habilitado=true).
 * Así nunca hay dos beans {@link PushNotificationSender}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mycar.fcm", name = "habilitado", havingValue = "false")
public class PushFallbackConfiguration {

    @Bean
    PushNotificationSender noOpPushSender() {
        return new NoOpPushSender();
    }
}
