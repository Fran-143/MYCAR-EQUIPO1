package backend.recuperacion.web;

import backend.recuperacion.RecoverPassword;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/recuperacion")
public class RecoveryController {
    private final RecoverPassword recovery;

    public RecoveryController(RecoverPassword recovery) { this.recovery = recovery; }

    public record CorreoRequest(String correo) { }
    public record VerificarRequest(String correo, String codigo) { }
    public record RestablecerRequest(String correo, String codigo, String nuevaContrasena,
            String confirmacionContrasena) { }

    @PostMapping(path = "/emitir", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> emitir(@RequestBody CorreoRequest body) {
        var result = recovery.emit(body == null ? null : body.correo());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resultado", result.resultado());
        payload.put("mensaje", "Enviamos un código temporal a tu correo.");
        if (result.codigoDesarrollo() != null) payload.put("codigoDesarrollo", result.codigoDesarrollo());
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore()).body(payload);
    }

    @PostMapping(path = "/verificar", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, String>> verificar(@RequestBody VerificarRequest body) {
        if (body == null) throw backend.recuperacion.RecoveryException.validation(Map.of("solicitud", "Datos obligatorios."));
        recovery.verify(body.correo(), body.codigo());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("resultado", "CODIGO_VALIDO"));
    }

    @PostMapping(path = "/contrasena", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, String>> restablecer(@RequestBody RestablecerRequest body) {
        if (body == null) throw backend.recuperacion.RecoveryException.validation(Map.of("solicitud", "Datos obligatorios."));
        recovery.resetPassword(body.correo(), body.codigo(), body.nuevaContrasena(), body.confirmacionContrasena());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("resultado", "CONTRASENA_RESTABLECIDA", "destino", "INICIO_SESION"));
    }
}
