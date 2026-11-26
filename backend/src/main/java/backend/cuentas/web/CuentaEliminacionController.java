package backend.cuentas.web;

import backend.autenticacion.SessionAuthentication;
import backend.cuentas.EliminacionCuentaService;
import backend.cuentas.ReactivacionAsistidaService;
import backend.cuentas.ReactivacionCuentaService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cuenta")
public class CuentaEliminacionController {
    private final EliminacionCuentaService eliminacion;
    private final ReactivacionCuentaService reactivacion;
    private final ReactivacionAsistidaService reactivacionAsistida;

    public CuentaEliminacionController(EliminacionCuentaService eliminacion, ReactivacionCuentaService reactivacion,
            ReactivacionAsistidaService reactivacionAsistida) {
        this.eliminacion = eliminacion;
        this.reactivacion = reactivacion;
        this.reactivacionAsistida = reactivacionAsistida;
    }

    public record OtpRequest(String codigo) { }
    public record ConfirmarEliminacionRequest(String codigo, String motivoCodigo, String motivoTexto) { }
    public record ReactivarInicioRequest(String correo, String contrasena, UUID idInstalacion) { }
    public record ReactivarOtpRequest(String correo, String codigo) { }
    public record AsistidaCorreoRequest(String correo) { }
    public record AsistidaConfirmarRequest(String correo, String codigo) { }

    @PostMapping(path = "/eliminacion/iniciar", produces = "application/json")
    public ResponseEntity<Map<String, Object>> iniciarEliminacion(Authentication auth) {
        long userId = userId(auth);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(eliminacion.iniciar(userId, Instant.now()));
    }

    @PostMapping(path = "/eliminacion/otp", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> otpEliminacion(Authentication auth, @RequestBody OtpRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(eliminacion.verificarOtp(userId(auth), body == null ? null : body.codigo(), Instant.now()));
    }

    @PostMapping(path = "/eliminacion/confirmar", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> confirmarEliminacion(Authentication auth,
            @RequestBody ConfirmarEliminacionRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(eliminacion.confirmar(userId(auth),
                        body == null ? null : body.codigo(),
                        body == null ? null : body.motivoCodigo(),
                        body == null ? null : body.motivoTexto(),
                        Instant.now()));
    }

    @PostMapping(path = "/reactivacion/iniciar", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> iniciarReactivacion(@RequestBody ReactivarInicioRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reactivacion.iniciar(
                        body == null ? null : body.correo(),
                        body == null ? null : body.contrasena(),
                        Instant.now()));
    }

    @PostMapping(path = "/reactivacion/otp", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> otpReactivacion(@RequestBody ReactivarOtpRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reactivacion.verificarOtp(
                        body == null ? null : body.correo(),
                        body == null ? null : body.codigo(),
                        Instant.now()));
    }

    @PostMapping(path = "/reactivacion/confirmar", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> confirmarReactivacion(@RequestBody ReactivarOtpRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reactivacion.confirmar(
                        body == null ? null : body.correo(),
                        body == null ? null : body.codigo(),
                        Instant.now()));
    }

    /** Titular confirma OTP de asistencia (sin sesión normal). */
    @PostMapping(path = "/reactivacion/asistida/estado", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> estadoAsistida(@RequestBody AsistidaCorreoRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reactivacionAsistida.estadoTitularPublico(
                        body == null ? null : body.correo(), Instant.now()));
    }

    @PostMapping(path = "/reactivacion/asistida/confirmar-titular", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> confirmarTitularAsistida(@RequestBody AsistidaConfirmarRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reactivacionAsistida.confirmarTitular(
                        body == null ? null : body.correo(),
                        body == null ? null : body.codigo(),
                        Instant.now()));
    }

    private static long userId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof SessionAuthentication.Principal p)) {
            throw backend.cuentas.CuentaException.forbidden("Sesión requerida.");
        }
        return p.userId();
    }
}
