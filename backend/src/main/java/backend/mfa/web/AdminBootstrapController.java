package backend.mfa.web;

import backend.mfa.MfaFailure;
import backend.mfa.persistence.JdbcMfaStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Aprovisionamiento ADMIN autorizado por secreto de entorno.
 * Deshabilitado por defecto: requiere MYCAR_ADMIN_BOOTSTRAP_SECRET no vacío
 * y mycar.mfa.bootstrap-enabled=true. No forma parte del contrato Android.
 * En Render: dejar bootstrap-enabled=false salvo ventana de aprovisionamiento.
 */
@RestController
@RequestMapping("/api/v1/ops/admin-bootstrap")
public class AdminBootstrapController {
    public record BootstrapRequest(String correo,String secretoBootstrap){
        @Override public String toString(){return "BootstrapRequest[REDACTED]";}
    }

    private final JdbcMfaStore mfa;
    private final String expectedSecret;
    private final boolean bootstrapEnabled;

    public AdminBootstrapController(JdbcMfaStore mfa,
            @Value("${mycar.mfa.bootstrap-secret:}") String expectedSecret,
            @Value("${mycar.mfa.bootstrap-enabled:false}") boolean bootstrapEnabled){
        this.mfa=mfa;
        this.expectedSecret=expectedSecret==null?"":expectedSecret;
        this.bootstrapEnabled=bootstrapEnabled;
    }

    @PostMapping(consumes="application/json",produces="application/json")
    @Transactional
    public ResponseEntity<Map<String,Object>> bootstrap(@RequestBody BootstrapRequest request){
        if(!bootstrapEnabled || expectedSecret.isBlank())throw MfaFailure.forbidden();
        if(request==null || request.correo()==null || request.secretoBootstrap()==null)throw MfaFailure.invalid(Map.of("solicitud","Datos obligatorios."));
        if(!constantEquals(expectedSecret,request.secretoBootstrap()))throw MfaFailure.forbidden();
        String email=request.correo().trim().toLowerCase();
        Long userId=mfa.findUserIdByEmail(email).orElse(null);
        if(userId==null)throw MfaFailure.invalid(Map.of("correo","Cuenta inexistente."));
        mfa.marcarAdmin(userId);
        // Sin secreto MFA aún: el primer login exigirá enroll (sin fila o PENDIENTE).
        mfa.auditar(userId,null,"BOOTSTRAP_ADMIN",null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("resultado","ADMIN_HABILITADO","idUsuario",userId,"mfa","PENDIENTE_EN_PRIMER_LOGIN"));
    }

    private static boolean constantEquals(String a,String b){
        byte[] x=a.getBytes(StandardCharsets.UTF_8),y=b.getBytes(StandardCharsets.UTF_8);
        if(x.length!=y.length){
            MessageDigest.isEqual(x,x);
            return false;
        }
        return MessageDigest.isEqual(x,y);
    }
}
