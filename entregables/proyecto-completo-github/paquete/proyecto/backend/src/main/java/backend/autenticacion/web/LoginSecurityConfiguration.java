package backend.autenticacion.web;

import backend.autenticacion.SessionAuthentication;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods=false)
public class LoginSecurityConfiguration {
    @Bean @Order(2)
    SecurityFilterChain loginPublicSecurity(HttpSecurity http)throws Exception{
        return stateless(http.securityMatcher("/api/v1/auth/login","/api/v1/auth/renovar"))
                .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.POST,"/api/v1/auth/login","/api/v1/auth/renovar")
                        .permitAll().anyRequest().denyAll()).build();
    }
    @Bean @Order(3)
    SecurityFilterChain sessionSecurity(HttpSecurity http,SessionAuthentication authentication,ObjectMapper json)throws Exception{
        return stateless(http.securityMatcher("/api/v1/auth/sesion"))
                .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.GET,"/api/v1/auth/sesion").authenticated().anyRequest().denyAll())
                .addFilterBefore(new SessionBearerFilter(authentication,json),AnonymousAuthenticationFilter.class).build();
    }
    private HttpSecurity stateless(HttpSecurity http)throws Exception{
        return http.csrf(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable);
    }
    // Registration's order-1 chain and order-100 deny-all/health fallback remain unchanged.
}
