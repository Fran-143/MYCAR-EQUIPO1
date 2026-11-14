package backend.talleres.seguridad;

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
public class TalleresAdminSecurityConfiguration {
    @Bean @Order(33)
    SecurityFilterChain talleresAdminSecurity(
            HttpSecurity http, TallerAdminSecretAuthenticator authenticator) throws Exception {
        return http.securityMatcher("/api/v1/rf34/admin/**")
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/rf34/admin/talleres-globales")
                        .hasAuthority("ROLE_TALLER_ADMIN")
                        .anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(
                        new TallerAdminSecretFilter(authenticator),
                        AnonymousAuthenticationFilter.class)
                .build();
    }
}
