package backend.localtest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Endpoints /api/v1/local-test/**: solo existen si mycar.local-test.enabled=true.
 * En neon/render quedan deshabilitados (propiedad false) y esta cadena no se registra.
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "mycar.local-test.enabled", havingValue = "true")
@Configuration(proxyBeanMethods = false)
public class LocalTestSecurityConfiguration {

    @Bean
    @Order(3)
    SecurityFilterChain localTestSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/v1/local-test/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/local-test/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/local-test/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
