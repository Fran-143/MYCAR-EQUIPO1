package backend.cuentas.web;

import backend.autenticacion.SessionAuthentication;
import backend.cuentas.CuentaAdminService;
import backend.cuentas.EjecutarEliminacionVencida;
import backend.cuentas.ReactivacionAsistidaService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/usuarios")
public class AdminUsuariosController {
    private final CuentaAdminService cuentas;
    private final EjecutarEliminacionVencida eliminacionVencida;
    private final ReactivacionAsistidaService reactivacionAsistida;

    public AdminUsuariosController(CuentaAdminService cuentas, EjecutarEliminacionVencida eliminacionVencida,
            ReactivacionAsistidaService reactivacionAsistida) {
        this.cuentas = cuentas;
        this.eliminacionVencida = eliminacionVencida;
        this.reactivacionAsistida = reactivacionAsistida;
    }

    public record MotivoRequest(String motivo, String comentario) { }
    public record BloquearRequest(String motivo, Integer duracionMinutos, String observacion) { }
    public record ReactivarAsistidaRequest(String motivo, String observacion) { }
    public record VerificarCodigoAsistidaRequest(String codigo) { }

    @GetMapping(produces = "application/json")
    public ResponseEntity<Map<String, Object>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String rol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(cuentas.listar(q, estado, rol, page, size, true));
    }

    @GetMapping(path = "/{id}", produces = "application/json")
    public ResponseEntity<Map<String, Object>> detalle(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuentas.detalle(id));
    }

    @GetMapping(path = "/{id}/historial", produces = "application/json")
    public ResponseEntity<?> historial(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuentas.historial(id));
    }

    @PostMapping(path = "/{id}/suspender", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> suspender(Authentication auth, @PathVariable long id,
            @RequestBody MotivoRequest body) {
        var p = principal(auth);
        cuentas.exigirAdminMfa(p);
        return ResponseEntity.ok(cuentas.suspender(p.userId(), id,
                body == null ? null : body.motivo(), body == null ? null : body.comentario()));
    }

    @PostMapping(path = "/{id}/reactivar", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> reactivar(Authentication auth, @PathVariable long id,
            @RequestBody MotivoRequest body) {
        var p = principal(auth);
        cuentas.exigirAdminMfa(p);
        return ResponseEntity.ok(cuentas.levantarSuspension(p.userId(), id, body == null ? null : body.motivo()));
    }

    @PostMapping(path = "/{id}/bloquear", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> bloquear(Authentication auth, @PathVariable long id,
            @RequestBody BloquearRequest body) {
        var p = principal(auth);
        cuentas.exigirAdminMfa(p);
        int mins = body == null || body.duracionMinutos() == null ? 0 : body.duracionMinutos();
        return ResponseEntity.ok(cuentas.bloquear(p.userId(), id,
                body == null ? null : body.motivo(), mins, body == null ? null : body.observacion()));
    }

    @PostMapping(path = "/{id}/desbloquear", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> desbloquear(Authentication auth, @PathVariable long id,
            @RequestBody MotivoRequest body) {
        var p = principal(auth);
        cuentas.exigirAdminMfa(p);
        return ResponseEntity.ok(cuentas.desbloquear(p.userId(), id, body == null ? null : body.motivo()));
    }

    @GetMapping(path = "/{id}/asistencia-recuperacion", produces = "application/json")
    public ResponseEntity<Map<String, Object>> asistencia(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuentas.asistenciaRecuperacion(id));
    }

    @GetMapping(path = "/{id}/reactivacion-asistida", produces = "application/json")
    public ResponseEntity<Map<String, Object>> estadoReactivacionAsistida(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reactivacionAsistida.estadoParaAdmin(id, Instant.now()));
    }

    @PostMapping(path = "/{id}/reactivacion-asistida/iniciar-verificacion", produces = "application/json")
    public ResponseEntity<Map<String, Object>> iniciarVerificacionTitular(Authentication auth, @PathVariable long id) {
        var p = principal(auth);
        cuentas.exigirAdminMfa(p);
        return ResponseEntity.ok(reactivacionAsistida.iniciarVerificacion(p.userId(), id, Instant.now()));
    }

    @PostMapping(path = "/{id}/reactivacion-asistida/verificar-codigo", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> verificarCodigoAsistida(Authentication auth, @PathVariable long id,
            @RequestBody VerificarCodigoAsistidaRequest body) {
        var p = principal(auth);
        cuentas.exigirAdminMfa(p);
        return ResponseEntity.ok(reactivacionAsistida.verificarCodigoAdmin(
                p.userId(), id, body == null ? null : body.codigo(), Instant.now()));
    }

    @PostMapping(path = "/{id}/reactivacion-asistida/confirmar", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> confirmarReactivacionAsistida(Authentication auth, @PathVariable long id,
            @RequestBody ReactivarAsistidaRequest body) {
        var p = principal(auth);
        cuentas.exigirAdminMfa(p);
        return ResponseEntity.ok(reactivacionAsistida.reactivarAdmin(
                p.userId(), id,
                body == null ? null : body.motivo(),
                body == null ? null : body.observacion(),
                Instant.now()));
    }

    /** Ejecución local de bajas vencidas (idempotente). No implica cron productivo. */
    @PostMapping(path = "/mantenimiento/eliminar-vencidas", produces = "application/json")
    public ResponseEntity<Map<String, Object>> eliminarVencidas(Authentication auth,
            @RequestParam(defaultValue = "20") int limite) {
        var p = principal(auth);
        cuentas.exigirAdminMfa(p);
        return ResponseEntity.ok(eliminacionVencida.ejecutar(Instant.now(), limite));
    }

    private static SessionAuthentication.Principal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof SessionAuthentication.Principal p) || !p.isAdmin()) {
            throw backend.cuentas.CuentaException.forbidden("Se requiere administrador.");
        }
        return p;
    }
}
