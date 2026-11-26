package backend.push.config;

import static org.assertj.core.api.Assertions.assertThat;

import backend.push.NoOpPushSender;
import backend.push.PushNotificationSender;
import backend.push.fcm.FcmConfiguration;
import backend.push.fcm.FcmPushSender;
import com.google.firebase.FirebaseApp;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Simula la selección de beans bajo perfiles neon/render sin arrancar JDBC.
 * No envía push reales.
 */
class PushSenderNeonRenderProfileTest {

    @AfterEach
    void limpiarFirebase() {
        List.copyOf(FirebaseApp.getApps()).forEach(FirebaseApp::delete);
    }

    @Test
    void perfilNeonLocalSinFcmUsaNoOpUnico() {
        new ApplicationContextRunner()
                .withUserConfiguration(FcmConfiguration.class, PushFallbackConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=neon",
                        "mycar.fcm.habilitado=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(PushNotificationSender.class);
                    assertThat(ctx.getBean(PushNotificationSender.class)).isInstanceOf(NoOpPushSender.class);
                });
    }

    @Test
    void perfilNeonRenderConSaUsaSoloFcm() {
        String b64 = Base64.getEncoder().encodeToString(
                PushSenderBeanSelectionTest.saJsonSimulado().getBytes(StandardCharsets.UTF_8));
        new ApplicationContextRunner()
                .withUserConfiguration(FcmConfiguration.class, PushFallbackConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=neon,render",
                        "mycar.fcm.habilitado=true",
                        "mycar.fcm.credentials-base64=" + b64)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBeansOfType(PushNotificationSender.class)).hasSize(1);
                    assertThat(ctx.getBean(PushNotificationSender.class)).isInstanceOf(FcmPushSender.class);
                });
    }
}
