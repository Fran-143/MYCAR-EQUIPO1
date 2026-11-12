package backend.alertas.seguridad;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(CronSecretProperties.class)
public class CronInternoSecurityConfiguration {

    @Bean
    @Order(14)
    SecurityFilterChain cronInternoSecurity(HttpSecurity http, CronSecretAuthenticator authenticator)
            throws Exception {
        return http.securityMatcher("/api/v1/interno/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/interno/alertas/evaluar-programadas")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/interno/push/despachar-recordatorios")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .addFilterBefore(new CronSecretFilter(authenticator), AnonymousAuthenticationFilter.class)
                .build();
    }
}
