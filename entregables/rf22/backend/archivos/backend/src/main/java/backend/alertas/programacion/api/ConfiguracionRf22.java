package backend.alertas.programacion.api;

import backend.autenticacion.SessionAuthentication;
import backend.autenticacion.web.SessionBearerFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods=false)
public class ConfiguracionRf22 {
    @Bean("rf22Clock") public Clock reloj() { return Clock.system(ZoneId.of("America/Lima")); }
    @Configuration(proxyBeanMethods=false)
    @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix="mycar.rf22",name="api-habilitada",havingValue="true")
    static class Seguridad {
        @Bean @Order(22)
        SecurityFilterChain rf22Security(HttpSecurity http,SessionAuthentication sesiones,ObjectMapper json) throws Exception {
            return http.securityMatcher("/api/v1/rf22/**")
                .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.GET,"/api/v1/rf22/contexto","/api/v1/rf22/vehiculos/*/antecedentes","/api/v1/rf22/operaciones/*").authenticated()
                    .requestMatchers(HttpMethod.POST,"/api/v1/rf22/revisiones","/api/v1/rf22/alertas").authenticated().anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(new SessionBearerFilter(sesiones,json),AnonymousAuthenticationFilter.class).build();
        }
    }
}
