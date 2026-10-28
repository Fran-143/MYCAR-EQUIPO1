package backend.historial.seguridad;

import backend.autenticacion.SessionAuthentication;
import backend.autenticacion.web.SessionBearerFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/** Política exclusiva de RF18. Reutiliza JWT, sesión persistida, revocación y cuenta activa de RF02. */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mycar.rf18", name = "api-habilitada", havingValue = "true")
public class HistorialSecurityConfiguration {
    @Bean @Order(18)
    SecurityFilterChain historialSecurity(HttpSecurity http, SessionAuthentication sesiones, ObjectMapper json) throws Exception {
        return http.securityMatcher("/api/v1/rf18/**")
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET,
                        "/api/v1/rf18/contexto", "/api/v1/rf18/vehiculos/*/mantenimientos").authenticated()
                        .anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(new SessionBearerFilter(sesiones, json), AnonymousAuthenticationFilter.class)
                .build();
    }
}
