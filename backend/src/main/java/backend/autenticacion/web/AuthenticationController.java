package backend.autenticacion.web;

import backend.autenticacion.LoginCommand;
import backend.autenticacion.SessionAuthentication;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/** Provisional RF02 REST contract, authorized for implementation but not frozen. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    public record LoginRequest(String correo,String contrasena,UUID idInstalacion){
        @Override public String toString(){return "LoginRequest[REDACTED]";}
    }
    public record RenewalRequest(UUID idInstalacion,String refreshToken){
        @Override public String toString(){return "RenewalRequest[REDACTED]";}
    }
    public record Tokens(String estado,long idSesion,String accessToken,Instant accessTokenExpiraUtc,
                         String refreshToken,Instant sesionExpiraUtc){
        @Override public String toString(){return "Tokens[REDACTED]";}
    }
    public record SessionStatus(boolean vigente,long idSesion,Instant sesionExpiraUtc){ }
    private final SessionAuthentication authentication;
    public AuthenticationController(SessionAuthentication authentication){this.authentication=authentication;}

    @PostMapping(value="/login",consumes="application/json",produces="application/json")
    public ResponseEntity<Tokens> login(@RequestBody LoginRequest request){
        return tokens(authentication.login(new LoginCommand(request.correo(),request.contrasena(),request.idInstalacion())));
    }
    @PostMapping(value="/renovar",consumes="application/json",produces="application/json")
    public ResponseEntity<Tokens> renew(@RequestBody RenewalRequest request){
        return tokens(authentication.renew(request.idInstalacion(),request.refreshToken()));
    }
    @GetMapping(value="/sesion",produces="application/json")
    public ResponseEntity<SessionStatus> session(@AuthenticationPrincipal SessionAuthentication.Principal principal){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SessionStatus(true,principal.sessionId(),principal.sessionExpiresAt()));
    }
    private ResponseEntity<Tokens> tokens(SessionAuthentication.Issued issued){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache")
                .body(new Tokens("AUTENTICADO",issued.idSesion(),issued.accessToken(),issued.accessTokenExpiraUtc(),issued.refreshToken(),issued.sesionExpiraUtc()));
    }
}
