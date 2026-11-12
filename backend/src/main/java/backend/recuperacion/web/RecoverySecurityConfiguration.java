package backend.recuperacion.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
public class RecoverySecurityConfiguration {
    @Bean
    @Order(6)
    SecurityFilterChain recoverySecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/v1/auth/recuperacion", "/api/v1/auth/recuperacion/**")
                .csrf(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/recuperacion/emitir",
                                "/api/v1/auth/recuperacion/verificar",
                                "/api/v1/auth/recuperacion/contrasena").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
