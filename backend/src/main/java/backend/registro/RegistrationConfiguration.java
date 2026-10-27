package backend.registro;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class RegistrationConfiguration {
    @Bean
    public Clock registrationClock() { return Clock.systemUTC(); }

    @Bean
    public PasswordEncoder registrationPasswordEncoder() {
        // Argon2id v19: salt 16 B, hash 32 B, p=1, memory 19 MiB, iterations=2.
        // OWASP Password Storage Cheat Sheet minimum; benchmark before production deployment.
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }
}
