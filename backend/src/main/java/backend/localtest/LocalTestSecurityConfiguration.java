package backend.localtest;

import backend.autenticacion.SessionAuthentication;
import backend.autenticacion.web.SessionBearerFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Endpoints /api/v1/local-test/**: solo existen si mycar.local-test.enabled=true.
 * En neon/render quedan deshabilitados (propiedad false) y esta cadena no se registra.
 * pagos-prueba exige sesión + header QA; el resto permanece permitAll con token en controlador.
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "mycar.local-test.enabled", havingValue = "true")
@Configuration(proxyBeanMethods = false)
public class LocalTestSecurityConfiguration {

    @Bean
    @Order(3)
    SecurityFilterChain localTestSecurity(
            HttpSecurity http, SessionAuthentication sesiones, ObjectMapper json) throws Exception {
        return http.securityMatcher("/api/v1/local-test/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/local-test/pagos-prueba",
                                "/api/v1/local-test/pagos-prueba/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/local-test/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/local-test/**").permitAll()
                        .anyRequest().denyAll())
                .addFilterBefore(new SessionBearerFilter(sesiones, json), AnonymousAuthenticationFilter.class)
                .build();
    }
}
