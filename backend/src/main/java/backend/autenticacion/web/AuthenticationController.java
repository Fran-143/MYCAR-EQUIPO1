package backend.autenticacion.web;

import backend.autenticacion.LoginCommand;
import backend.autenticacion.SessionAuthentication;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
    public record SessionStatus(boolean vigente,long idSesion,Instant sesionExpiraUtc,String alcance,String rol){ }
    private final SessionAuthentication authentication;
    public AuthenticationController(SessionAuthentication authentication){this.authentication=authentication;}

    @PostMapping(value="/login",consumes="application/json",produces="application/json")
    public ResponseEntity<Map<String,Object>> login(@RequestBody LoginRequest request){
        var result=authentication.loginResult(new LoginCommand(request.correo(),request.contrasena(),request.idInstalacion()));
        if(result instanceof SessionAuthentication.LoginMfaChallenge mfa){
            var c=mfa.challenge();
            Map<String,Object> body=new LinkedHashMap<>();
            body.put("estado",c.estado());
            body.put("desafioId",c.desafioId());
            body.put("idSesion",c.idSesion());
            body.put("accessToken",c.accessToken());
            body.put("accessTokenExpiraUtc",c.accessTokenExpiraUtc());
            body.put("refreshToken",c.refreshToken());
            body.put("sesionExpiraUtc",c.sesionExpiraUtc());
            body.put("alcance",c.alcance());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);
        }
        var auth=(SessionAuthentication.LoginAuthenticated)result;
        return tokens(auth.issued(),auth.alcance(),"AUTENTICADO");
    }
    @PostMapping(value="/renovar",consumes="application/json",produces="application/json")
    public ResponseEntity<Map<String,Object>> renew(@RequestBody RenewalRequest request){
        var issued=authentication.renew(request.idInstalacion(),request.refreshToken());
        return tokens(issued,issued.alcance(),"AUTENTICADO");
    }
    @GetMapping(value="/sesion",produces="application/json")
    public ResponseEntity<SessionStatus> session(@AuthenticationPrincipal SessionAuthentication.Principal principal){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SessionStatus(true,principal.sessionId(),principal.sessionExpiresAt(),principal.alcance(),principal.role()));
    }
    private ResponseEntity<Map<String,Object>> tokens(SessionAuthentication.Issued issued,String alcance,String estado){
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("estado",estado);
        body.put("idSesion",issued.idSesion());
        body.put("accessToken",issued.accessToken());
        body.put("accessTokenExpiraUtc",issued.accessTokenExpiraUtc());
        body.put("refreshToken",issued.refreshToken());
        body.put("sesionExpiraUtc",issued.sesionExpiraUtc());
        body.put("alcance",alcance);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);
    }
}
