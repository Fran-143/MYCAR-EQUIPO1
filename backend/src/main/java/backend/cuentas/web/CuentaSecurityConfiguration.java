package backend.cuentas.web;

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
public class CuentaSecurityConfiguration {

    @Bean
    @Order(7)
    SecurityFilterChain cuentaSecurity(HttpSecurity http, SessionAuthentication authentication,
            ObjectMapper json) throws Exception {
        return http.securityMatcher("/api/v1/cuenta/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/cuenta/reactivacion/iniciar",
                                "/api/v1/cuenta/reactivacion/otp",
                                "/api/v1/cuenta/reactivacion/confirmar",
                                "/api/v1/cuenta/reactivacion/asistida/estado",
                                "/api/v1/cuenta/reactivacion/asistida/confirmar-titular")
                        .permitAll()
                        .requestMatchers("/api/v1/cuenta/eliminacion/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new SessionBearerFilter(authentication, json), AnonymousAuthenticationFilter.class)
                .build();
    }
}
