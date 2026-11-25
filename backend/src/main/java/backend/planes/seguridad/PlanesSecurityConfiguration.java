package backend.planes.seguridad;

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

@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
public class PlanesSecurityConfiguration {

    @Bean
    @Order(8)
    SecurityFilterChain planesSecurity(HttpSecurity http, SessionAuthentication sesiones, ObjectMapper json)
            throws Exception {
        return http.securityMatcher(
                        "/api/v1/planes",
                        "/api/v1/planes/**",
                        "/api/v1/suscripciones",
                        "/api/v1/suscripciones/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/planes", "/api/v1/planes/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/suscripciones/mia",
                                "/api/v1/suscripciones/mia/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/suscripciones/solicitudes/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new SessionBearerFilter(sesiones, json), AnonymousAuthenticationFilter.class)
                .build();
    }
}
