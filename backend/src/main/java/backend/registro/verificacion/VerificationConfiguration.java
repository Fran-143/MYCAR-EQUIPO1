package backend.registro.verificacion;

import backend.registro.RegisterAccount;
import backend.registro.RegistrationPrivacyPolicy;
import backend.registro.RegistrationStore;
import backend.registro.RegistrationValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
public class VerificationConfiguration {

    @Bean
    VerificationRepository verificationRepository(DataSource dataSource,
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        return new VerificationRepository(dataSource, transactionManager);
    }

    @Bean
    VerificationCrypto verificationCrypto(
            @Value("${mycar.verificacion.crypto.otp-key:}") String otp,
            @Value("${mycar.verificacion.crypto.fingerprint-key:}") String fingerprint,
            @Value("${mycar.verificacion.crypto.index-key:}") String index,
            @Value("${mycar.verificacion.crypto.encryption-key:}") String encryption) {
        return new VerificationCrypto(
                decodeOrDerive(otp, "mycar-dev-otp-key-v1"),
                decodeOrDerive(fingerprint, "mycar-dev-fingerprint-key-v1"),
                decodeOrDerive(index, "mycar-dev-index-key-v1"),
                decodeOrDerive(encryption, "mycar-dev-encryption-key-v1"));
    }

    @Bean
    @ConditionalOnProperty(name = "mycar.verificacion.mail.modo", havingValue = "desarrollo", matchIfMissing = true)
    DevMailGateway mailGateway() {
        return new DevMailGateway();
    }

    @Bean
    @ConditionalOnProperty(name = "mycar.verificacion.mail.modo", havingValue = "smtp")
    SmtpMailGateway smtpMailGateway(
            JavaMailSender mailSender,
            @Value("${mycar.verificacion.mail.from:}") String from,
            @Value("${spring.mail.username:}") String mailUsername) {
        String resolvedFrom = StringUtils.hasText(from) ? from.trim()
                : (StringUtils.hasText(mailUsername) ? mailUsername.trim() : "");
        if (!StringUtils.hasText(resolvedFrom)) {
            throw new IllegalStateException(
                    "SMTP activo: configura mycar.verificacion.mail.from o spring.mail.username en .env");
        }
        if (!StringUtils.hasText(mailUsername)) {
            throw new IllegalStateException(
                    "SMTP activo: configura spring.mail.username y spring.mail.password en .env");
        }
        org.slf4j.LoggerFactory.getLogger(VerificationConfiguration.class)
                .warn("MailGateway modo=smtp activo (no Brevo HTTPS). En Render Free el SMTP a localhost no entrega correo.");
        return new SmtpMailGateway(mailSender, resolvedFrom);
    }

    /**
     * Brevo Free vía HTTPS (Render Free bloquea SMTP 465/587).
     * Activar con {@code mycar.verificacion.mail.modo=brevo} + API key en entorno.
     */
    @Bean
    @ConditionalOnProperty(name = "mycar.verificacion.mail.modo", havingValue = "brevo")
    BrevoMailGateway brevoMailGateway(
            @Value("${mycar.verificacion.brevo.api-key:}") String apiKey,
            @Value("${mycar.verificacion.mail.from:}") String from,
            @Value("${mycar.verificacion.brevo.from-name:MyCar}") String fromName,
            @Value("${mycar.verificacion.brevo.base-url:" + BrevoMailGateway.DEFAULT_BASE_URL + "}") String baseUrl) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "Brevo activo: configura mycar.verificacion.brevo.api-key (o MYCAR_VERIFICACION_BREVO_API_KEY)");
        }
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException(
                    "Brevo activo: configura mycar.verificacion.mail.from con un remitente verificado en Brevo");
        }
        String base = StringUtils.hasText(baseUrl) ? baseUrl.trim().replaceAll("/+$", "")
                : BrevoMailGateway.DEFAULT_BASE_URL;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(20));
        RestClient client = RestClient.builder()
                .baseUrl(base)
                .requestFactory(requestFactory)
                .defaultHeader("api-key", apiKey.trim())
                .defaultHeader("accept", "application/json")
                .build();
        String name = StringUtils.hasText(fromName) ? fromName.trim() : "MyCar";
        return new BrevoMailGateway(client, from.trim(), name);
    }

    @Bean
    VerificationService verificationService(VerificationRepository db, VerificationCrypto crypto,
            RegistrationValidator validator, RegistrationPrivacyPolicy privacy,
            RegistrationStore accounts, RegisterAccount register) {
        return new VerificationService(db, crypto, validator, privacy, accounts, register);
    }

    @Bean
    VerificationDispatcher verificationDispatcher(VerificationService verification, MailGateway mail) {
        return new VerificationDispatcher(verification, mail);
    }

    @Bean
    VerificationOriginService verificationOriginService(VerificationRepository db, VerificationCrypto crypto) {
        return new VerificationOriginService(db, crypto);
    }

    /** 32 bytes: Base64 URL/std, hex 64 chars, or deterministic derive from label (dev only). */
    static byte[] decodeOrDerive(String configured, String deriveLabel) {
        if (StringUtils.hasText(configured)) {
            String v = configured.trim();
            try {
                if (v.length() == 64 && v.matches("(?i)[0-9a-f]{64}")) {
                    byte[] out = new byte[32];
                    for (int i = 0; i < 32; i++) {
                        out[i] = (byte) Integer.parseInt(v.substring(i * 2, i * 2 + 2), 16);
                    }
                    return out;
                }
                byte[] decoded = Base64.getUrlDecoder().decode(v);
                if (decoded.length == 32) return decoded;
                decoded = Base64.getDecoder().decode(v);
                if (decoded.length == 32) return decoded;
            } catch (IllegalArgumentException ignored) {
                // fall through to derive
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(deriveLabel.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(digest, 32);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot derive verification key", ex);
        }
    }
}
