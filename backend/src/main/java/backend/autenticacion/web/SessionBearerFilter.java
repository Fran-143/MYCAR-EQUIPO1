package backend.autenticacion.web;

import backend.autenticacion.AuthenticationFailure;
import backend.autenticacion.SessionAuthentication;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Constructed inside its security chain; never registered as a global servlet filter. */
public final class SessionBearerFilter extends OncePerRequestFilter {
    private final SessionAuthentication authentication;
    private final ObjectMapper json;
    public SessionBearerFilter(SessionAuthentication authentication,ObjectMapper json){this.authentication=authentication;this.json=json;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException{
        String header=request.getHeader("Authorization");
        // Sin Bearer: dejar anónimo para que permitAll (p. ej. RF51 reactivación) funcione.
        // Endpoints authenticated() rechazarán con 403 si no hay principal.
        if(header==null || !header.regionMatches(true,0,"Bearer ",0,7) || header.length()==7){
            SecurityContextHolder.clearContext();
            chain.doFilter(request,response);
            return;
        }
        try{
            var principal=authentication.validateAccess(header.substring(7));
            List<SimpleGrantedAuthority> authorities=new ArrayList<>(2);
            String alcance=principal.alcance()==null?"USUARIO":principal.alcance();
            switch(alcance){
                case "ADMIN" -> {
                    if(!"ADMIN".equals(principal.role()))throw AuthenticationFailure.session();
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                }
                case "MFA_CHALLENGE" -> authorities.add(new SimpleGrantedAuthority("ROLE_MFA_CHALLENGE"));
                case "MFA_REENROLL" -> authorities.add(new SimpleGrantedAuthority("ROLE_MFA_REENROLL"));
                default -> authorities.add(new SimpleGrantedAuthority("ROLE_USUARIO"));
            }
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,authorities));
            SecurityContextHolder.setContext(context);
        }catch(Exception ex){SecurityContextHolder.clearContext();AuthenticationErrors.write(response,json,ex);return;}
        chain.doFilter(request,response);
    }
}
