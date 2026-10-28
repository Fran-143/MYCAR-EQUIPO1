package backend.alertas.seguridad;

import backend.autenticacion.SessionAuthentication;
import backend.autenticacion.web.SessionBearerFilter;
import backend.alertas.api.SesionAlertasNoValidada;
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
@ConditionalOnProperty(prefix = "mycar.rf22", name = "api-habilitada", havingValue = "true")
public class IntegracionSesionAlertas {
    @Bean
    IdentidadAlertas identidadAlertas() {
        return autenticacion -> {
            if (autenticacion == null || !autenticacion.isAuthenticated()
                    || !(autenticacion.getPrincipal() instanceof SessionAuthentication.Principal p)
                    || p.userId() <= 0) {
                throw new SesionAlertasNoValidada();
            }
            return p.userId();
        };
    }

    @Bean
    @Order(22)
    SecurityFilterChain alertasSecurity(HttpSecurity http, SessionAuthentication sesiones, ObjectMapper json)
            throws Exception {
        return http.securityMatcher("/api/v1/rf22/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/rf22/contexto",
                                "/api/v1/rf22/vehiculos/*/alertas", "/api/v1/rf22/operaciones/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/rf22/vehiculos/*/alertas").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new SessionBearerFilter(sesiones, json), AnonymousAuthenticationFilter.class)
                .build();
    }
}
