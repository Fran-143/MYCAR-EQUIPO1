package backend.vehiculos.seguridad;

import backend.autenticacion.SessionAuthentication;
import backend.autenticacion.web.SessionBearerFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class SeguridadSesionVehiculos {
    @Bean @Order(10)
    SecurityFilterChain vehiculosSecurity(HttpSecurity http, SessionAuthentication sesiones, ObjectMapper json) throws Exception {
        return http.securityMatcher("/api/v1/vehiculos", "/api/v1/vehiculos/**")
                .csrf(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/vehiculos", "/api/v1/vehiculos/catalogos",
                                "/api/v1/vehiculos/cupo-registro", "/api/v1/vehiculos/identidad",
                                "/api/v1/vehiculos/operaciones/*", "/api/v1/vehiculos/kilometraje/operaciones/*",
                                "/api/v1/vehiculos/kilometraje/correcciones/operaciones/*",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/kilometraje/actual",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/kilometraje/lecturas",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/kilometraje/lecturas/{idLectura:[0-9]+}/correccion",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/soat",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/soat/{idSoat:[0-9]+}/documento"
                        ).authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/vehiculos",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/kilometraje/lecturas",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/kilometraje/lecturas/{idLectura:[0-9]+}/correcciones",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/soat",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/soat/renovar",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/soat/documentos/presign-upload"
                        ).authenticated()
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/principal",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/estado",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/soat/{idSoat:[0-9]+}",
                                "/api/v1/vehiculos/{idVehiculo:[0-9]+}/soat/{idSoat:[0-9]+}/recordatorio").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(new SessionBearerFilter(sesiones, json), AnonymousAuthenticationFilter.class)
                .build();
    }
}
