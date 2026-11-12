package backend.notificaciones.seguridad;

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

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "mycar.rf46", name = "api-habilitada", havingValue = "true", matchIfMissing = true)
public class PreferenciasNotificacionSecurityConfiguration {

    @Bean
    @Order(48)
    SecurityFilterChain preferenciasNotificacionSecurity(
            HttpSecurity http, SessionAuthentication sesiones, ObjectMapper json) throws Exception {
        return http.securityMatcher(
                        "/api/v1/preferencias-notificacion",
                        "/api/v1/preferencias-notificacion/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/preferencias-notificacion").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/preferencias-notificacion").authenticated()
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/v1/preferencias-notificacion/permiso-dispositivo").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new SessionBearerFilter(sesiones, json), AnonymousAuthenticationFilter.class)
                .build();
    }
}
