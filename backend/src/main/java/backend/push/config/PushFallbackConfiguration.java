package backend.push.config;

import backend.push.NoOpPushSender;
import backend.push.PushNotificationSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Garantiza un {@link PushNotificationSender} si FCM está deshabilitado por propiedad. */
@Configuration(proxyBeanMethods = false)
public class PushFallbackConfiguration {

    @Bean
    @ConditionalOnMissingBean(PushNotificationSender.class)
    PushNotificationSender noOpPushSender() {
        return new NoOpPushSender();
    }
}
