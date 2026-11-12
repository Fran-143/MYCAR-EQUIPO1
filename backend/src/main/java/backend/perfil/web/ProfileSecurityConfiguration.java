package backend.perfil.web;

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
public class ProfileSecurityConfiguration {
    @Bean
    @Order(5)
    SecurityFilterChain profileSecurity(HttpSecurity http, SessionAuthentication sessions, ObjectMapper json)
            throws Exception {
        return http.securityMatcher("/api/v1/perfil", "/api/v1/perfil/**")
                .csrf(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/perfil").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/perfil").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/perfil/contrasena").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/perfil/contrasena/verificar").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new SessionBearerFilter(sessions, json), AnonymousAuthenticationFilter.class)
                .build();
    }
}
