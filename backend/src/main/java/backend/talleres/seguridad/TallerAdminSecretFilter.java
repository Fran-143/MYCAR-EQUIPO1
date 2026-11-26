package backend.talleres.seguridad;

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

/** Header {@code X-MyCar-Taller-Admin-Secret} para rutas admin de catálogo. */
public class TallerAdminSecretFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-MyCar-Taller-Admin-Secret";

    private final TallerAdminSecretAuthenticator authenticator;

    public TallerAdminSecretFilter(TallerAdminSecretAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!authenticator.acepta(request.getHeader(HEADER))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"codigo\":\"TALLER_ADMIN_NO_AUTORIZADO\",\"mensaje\":\"No autorizado.\"}");
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken(
                "mycar-taller-admin",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_TALLER_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
