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
import java.util.List;

/** Constructed inside its security chain; never registered as a global servlet filter. */
public final class SessionBearerFilter extends OncePerRequestFilter {
    private final SessionAuthentication authentication;
    private final ObjectMapper json;
    public SessionBearerFilter(SessionAuthentication authentication,ObjectMapper json){this.authentication=authentication;this.json=json;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException{
        try{
            String header=request.getHeader("Authorization");
            if(header==null || !header.regionMatches(true,0,"Bearer ",0,7) || header.length()==7)
                throw AuthenticationFailure.session();
            var principal=authentication.validateAccess(header.substring(7));
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,
                    List.of(new SimpleGrantedAuthority("ROLE_USUARIO"))));
            SecurityContextHolder.setContext(context);
        }catch(Exception ex){SecurityContextHolder.clearContext();AuthenticationErrors.write(response,json,ex);return;}
        chain.doFilter(request,response);
    }
}
