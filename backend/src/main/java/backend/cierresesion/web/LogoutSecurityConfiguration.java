package backend.cierresesion.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods=false)
public class LogoutSecurityConfiguration {
    @Bean @Order(4)
    SecurityFilterChain logoutProofSecurity(HttpSecurity http)throws Exception{
        // Only this endpoint accepts JWT proof for an idempotent revocation. RF02's normal
        // bearer filter correctly rejects already-revoked sessions, so it is not changed/reused here.
        // permitAll means no ambient cookie/login; the application use case MUST verify JWT + DB ownership.
        return http.securityMatcher("/api/v1/auth/logout")
                .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.POST,"/api/v1/auth/logout").permitAll().anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).build();
    }
}
