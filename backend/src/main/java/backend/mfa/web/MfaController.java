package backend.mfa.web;

import backend.autenticacion.SessionAuthentication;
import backend.mfa.MfaFailure;
import backend.mfa.MfaService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/mfa")
public class MfaController {
    public record CodigoRequest(String codigo){ @Override public String toString(){return "CodigoRequest[REDACTED]";} }
    public record VerificarRequest(UUID desafioId,String codigo){ @Override public String toString(){return "VerificarRequest[REDACTED]";} }
    public record RecuperarRequest(UUID desafioId,String codigoRespaldo){ @Override public String toString(){return "RecuperarRequest[REDACTED]";} }

    private final MfaService mfa;
    public MfaController(MfaService mfa){this.mfa=mfa;}

    @PostMapping(value="/enroll/iniciar",produces="application/json")
    public ResponseEntity<Map<String,Object>> iniciar(@AuthenticationPrincipal SessionAuthentication.Principal principal){
        var start=mfa.iniciarEnroll(principal);
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("otpauthUri",start.otpauthUri());
        body.put("secretoManual",start.secretoManual());
        return ok(body);
    }

    @PostMapping(value="/enroll/confirmar",consumes="application/json",produces="application/json")
    public ResponseEntity<Map<String,Object>> confirmar(
            @AuthenticationPrincipal SessionAuthentication.Principal principal,@RequestBody CodigoRequest request){
        var codes=mfa.confirmarEnroll(principal,request==null?null:request.codigo());
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("estado","MFA_RECOVERY_EMITIDO");
        body.put("codigosRecuperacion",codes.codigos());
        body.put("loteId",codes.loteId());
        return ok(body);
    }

    @PostMapping(value="/enroll/confirmar-respaldo",produces="application/json")
    public ResponseEntity<Map<String,Object>> confirmarRespaldo(
            @AuthenticationPrincipal SessionAuthentication.Principal principal){
        var issued=mfa.confirmarRespaldo(principal);
        return tokens(issued,"ADMIN","AUTENTICADO");
    }

    @PostMapping(value="/verificar",consumes="application/json",produces="application/json")
    public ResponseEntity<Map<String,Object>> verificar(
            @AuthenticationPrincipal SessionAuthentication.Principal principal,@RequestBody VerificarRequest request){
        if(request==null)throw MfaFailure.invalid(Map.of("solicitud","Datos obligatorios."));
        var issued=mfa.verificar(principal,request.desafioId(),request.codigo());
        return tokens(issued,"ADMIN","AUTENTICADO");
    }

    @PostMapping(value="/recuperar",consumes="application/json",produces="application/json")
    public ResponseEntity<Map<String,Object>> recuperar(
            @AuthenticationPrincipal SessionAuthentication.Principal principal,@RequestBody RecuperarRequest request){
        if(request==null)throw MfaFailure.invalid(Map.of("solicitud","Datos obligatorios."));
        var issued=mfa.recuperar(principal,request.desafioId(),request.codigoRespaldo());
        return tokens(issued,"MFA_REENROLL","MFA_REENROLL_REQUERIDO");
    }

    private static ResponseEntity<Map<String,Object>> tokens(SessionAuthentication.Issued issued,String alcance,String estado){
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("estado",estado);
        body.put("idSesion",issued.idSesion());
        body.put("accessToken",issued.accessToken());
        body.put("accessTokenExpiraUtc",issued.accessTokenExpiraUtc());
        body.put("refreshToken",issued.refreshToken());
        body.put("sesionExpiraUtc",issued.sesionExpiraUtc());
        body.put("alcance",alcance);
        return ok(body);
    }
    private static ResponseEntity<Map<String,Object>> ok(Map<String,Object> body){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);
    }
}
