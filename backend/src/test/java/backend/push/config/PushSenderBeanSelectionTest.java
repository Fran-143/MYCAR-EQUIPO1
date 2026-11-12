package backend.push.config;

import static org.assertj.core.api.Assertions.assertThat;

import backend.push.NoOpPushSender;
import backend.push.PushNotificationSender;
import backend.push.fcm.FcmConfiguration;
import backend.push.fcm.FcmPushSender;
import com.google.firebase.FirebaseApp;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Verifica exclusión mutua: nunca dos {@link PushNotificationSender}.
 * No envía notificaciones reales.
 */
class PushSenderBeanSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FcmConfiguration.class, PushFallbackConfiguration.class);

    @AfterEach
    void limpiarFirebase() {
        List.copyOf(FirebaseApp.getApps()).forEach(FirebaseApp::delete);
    }

    @Test
    void habilitadoFalseSoloNoOp() {
        runner.withPropertyValues("mycar.fcm.habilitado=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(PushNotificationSender.class);
                    assertThat(ctx.getBean(PushNotificationSender.class)).isInstanceOf(NoOpPushSender.class);
                    assertThat(ctx).doesNotHaveBean("pushNotificationSender");
                });
    }

    @Test
    void habilitadoTrueSinCredencialesFallaArranque() {
        runner.withPropertyValues(
                        "mycar.fcm.habilitado=true",
                        "mycar.fcm.credentials-json=",
                        "mycar.fcm.credentials-base64=",
                        "mycar.fcm.credentials-path=")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void habilitadoTrueConSaSimuladaSoloFcm() {
        String saJson = saJsonSimulado();
        String b64 = Base64.getEncoder().encodeToString(saJson.getBytes(StandardCharsets.UTF_8));
        runner.withPropertyValues(
                        "mycar.fcm.habilitado=true",
                        "mycar.fcm.credentials-base64=" + b64)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(PushNotificationSender.class);
                    assertThat(ctx.getBean(PushNotificationSender.class)).isInstanceOf(FcmPushSender.class);
                    assertThat(ctx.getBeansOfType(PushNotificationSender.class)).hasSize(1);
                    assertThat(ctx.getBean(PushNotificationSender.class).proveedor()).isEqualTo("FCM");
                });
    }

    @Test
    void perfilesNeonRenderExigenUnSoloSenderConSa() {
        String b64 = Base64.getEncoder().encodeToString(saJsonSimulado().getBytes(StandardCharsets.UTF_8));
        new ApplicationContextRunner()
                .withUserConfiguration(FcmConfiguration.class, PushFallbackConfiguration.class, Marker.class)
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

    @Configuration
    static class Marker {
    }

    /** SA mínimo con RSA válida en memoria — no se usa para enviar. */
    static String saJsonSimulado() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            String pkcs8 = Base64.getMimeEncoder(64, new byte[] {'\n'})
                    .encodeToString(pair.getPrivate().getEncoded());
            String pem = "-----BEGIN PRIVATE KEY-----\\n"
                    + pkcs8.replace("\n", "\\n")
                    + "\\n-----END PRIVATE KEY-----\\n";
            return "{"
                    + "\"type\":\"service_account\","
                    + "\"project_id\":\"mycar-test\","
                    + "\"private_key_id\":\"test\","
                    + "\"private_key\":\"" + pem + "\","
                    + "\"client_email\":\"fcm-test@mycar-test.iam.gserviceaccount.com\","
                    + "\"client_id\":\"1\","
                    + "\"token_uri\":\"https://oauth2.googleapis.com/token\""
                    + "}";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
