package backend.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Evita caché intermedia (proxies/CDN) en alertas y notificaciones:
 * el estado RF23 debe verse al instante tras sync/atender.
 * Cloudflare en este proyecto es solo el cron Worker, no un proxy de la API;
 * el header sigue siendo defensa en profundidad.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class NoStoreAlertasNotificacionesFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && (path.startsWith("/api/v1/rf22") || path.startsWith("/api/v1/notificaciones"))) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        }
        filterChain.doFilter(request, response);
    }
}
