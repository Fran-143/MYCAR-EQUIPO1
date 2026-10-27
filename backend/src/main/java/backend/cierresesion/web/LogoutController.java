package backend.cierresesion.web;

import backend.autenticacion.AuthenticationFailure;
import backend.cierresesion.CloseSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogoutController {
    private final CloseSession closeSession;
    public LogoutController(CloseSession closeSession){this.closeSession=closeSession;}

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request){
        // Read directly, rather than binding/logging a raw secret as a controller argument DTO.
        String header=request.getHeader("Authorization");
        if(header==null || !header.regionMatches(true,0,"Bearer ",0,7) || header.length()==7)
            throw AuthenticationFailure.session();
        closeSession.close(header.substring(7));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
