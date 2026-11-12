package backend.alertas.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autenticación de servicio para rutas {@code /api/v1/interno/**}.
 * Header: {@code X-MyCar-Cron-Secret}. No usa JWT de usuario.
 */
public class CronSecretFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-MyCar-Cron-Secret";

    private final CronSecretAuthenticator authenticator;

    public CronSecretFilter(CronSecretAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!authenticator.acepta(request.getHeader(HEADER))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"codigo\":\"CRON_NO_AUTORIZADO\",\"mensaje\":\"No autorizado.\"}");
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken(
                "mycar-cron",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_CRON")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
