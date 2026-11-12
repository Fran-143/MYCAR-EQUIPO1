package backend.push.seguridad;

import backend.autenticacion.SessionAuthentication;
import backend.autenticacion.web.SessionBearerFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PushSecurityConfiguration {

    @Bean
    @Order(49)
    SecurityFilterChain pushSecurity(
            HttpSecurity http, SessionAuthentication sesiones, ObjectMapper json) throws Exception {
        return http.securityMatcher("/api/v1/dispositivos-push", "/api/v1/dispositivos-push/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/dispositivos-push/registrar")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/dispositivos-push/desvincular")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/dispositivos-push/probar")
                        .authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new SessionBearerFilter(sesiones, json), AnonymousAuthenticationFilter.class)
                .build();
    }
}
