package backend.talleres.seguridad;

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

@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mycar.rf34", name = "api-habilitada", havingValue = "true")
public class TalleresSecurityConfiguration {
    @Bean @Order(34)
    SecurityFilterChain talleresSecurity(HttpSecurity http, SessionAuthentication sesiones, ObjectMapper json)
            throws Exception {
        return http.securityMatcher("/api/v1/rf34/**")
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/rf34/talleres", "/api/v1/rf34/talleres/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/rf34/talleres",
                                "/api/v1/rf34/talleres/*/retirar",
                                "/api/v1/rf34/talleres/*/favorito").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/rf34/talleres/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/rf34/talleres/*/favorito").authenticated()
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
