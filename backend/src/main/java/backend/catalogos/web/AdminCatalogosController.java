package backend.catalogos.web;

import backend.autenticacion.SessionAuthentication;
import backend.catalogos.service.AseguradoraSoatAdminService;
import backend.catalogos.service.CatalogoAdminGuard;
import backend.catalogos.service.TallerGlobalAdminService;
import backend.catalogos.service.TipoMantenimientoAdminService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/catalogos")
public class AdminCatalogosController {
    private final CatalogoAdminGuard guard;
    private final TipoMantenimientoAdminService tipos;
    private final AseguradoraSoatAdminService aseguradoras;
    private final TallerGlobalAdminService talleres;

    public AdminCatalogosController(
            CatalogoAdminGuard guard,
            TipoMantenimientoAdminService tipos,
            AseguradoraSoatAdminService aseguradoras,
            TallerGlobalAdminService talleres) {
        this.guard = guard;
        this.tipos = tipos;
        this.aseguradoras = aseguradoras;
        this.talleres = talleres;
    }

    public record NombreBody(String nombre) { }

    // ---- Tipos mantenimiento ----

    @GetMapping(path = "/tipos-mantenimiento", produces = "application/json")
    public ResponseEntity<Map<String, Object>> listarTipos(
            Authentication auth,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "TODOS") String estado) {
        var p = guard.exigir(principal(auth));
        return ok(tipos.listar(q, estado));
    }

    @PostMapping(path = "/tipos-mantenimiento", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> crearTipo(Authentication auth, @RequestBody NombreBody body) {
        var p = guard.exigir(principal(auth));
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(tipos.crear(p.userId(), body == null ? null : body.nombre()));
    }

    @PutMapping(path = "/tipos-mantenimiento/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> editarTipo(
            Authentication auth, @PathVariable long id, @RequestBody NombreBody body) {
        var p = guard.exigir(principal(auth));
        return ok(tipos.editar(p.userId(), id, body == null ? null : body.nombre()));
    }

    @PostMapping(path = "/tipos-mantenimiento/{id}/desactivar", produces = "application/json")
    public ResponseEntity<Map<String, Object>> desactivarTipo(Authentication auth, @PathVariable long id) {
        var p = guard.exigir(principal(auth));
        return ok(tipos.desactivar(p.userId(), id));
    }

    @PostMapping(path = "/tipos-mantenimiento/{id}/reactivar", produces = "application/json")
    public ResponseEntity<Map<String, Object>> reactivarTipo(Authentication auth, @PathVariable long id) {
        var p = guard.exigir(principal(auth));
        return ok(tipos.reactivar(p.userId(), id));
    }

    // ---- Aseguradoras SOAT ----

    @GetMapping(path = "/aseguradoras-soat", produces = "application/json")
    public ResponseEntity<Map<String, Object>> listarAseguradoras(
            Authentication auth,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "TODOS") String estado) {
        guard.exigir(principal(auth));
        return ok(aseguradoras.listar(q, estado));
    }

    @PostMapping(path = "/aseguradoras-soat", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> crearAseguradora(Authentication auth, @RequestBody NombreBody body) {
        var p = guard.exigir(principal(auth));
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(aseguradoras.crear(p.userId(), body == null ? null : body.nombre()));
    }

    @PutMapping(path = "/aseguradoras-soat/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> editarAseguradora(
            Authentication auth, @PathVariable long id, @RequestBody NombreBody body) {
        var p = guard.exigir(principal(auth));
        return ok(aseguradoras.editar(p.userId(), id, body == null ? null : body.nombre()));
    }

    @PostMapping(path = "/aseguradoras-soat/{id}/desactivar", produces = "application/json")
    public ResponseEntity<Map<String, Object>> desactivarAseguradora(Authentication auth, @PathVariable long id) {
        var p = guard.exigir(principal(auth));
        return ok(aseguradoras.desactivar(p.userId(), id));
    }

    @PostMapping(path = "/aseguradoras-soat/{id}/reactivar", produces = "application/json")
    public ResponseEntity<Map<String, Object>> reactivarAseguradora(Authentication auth, @PathVariable long id) {
        var p = guard.exigir(principal(auth));
        return ok(aseguradoras.reactivar(p.userId(), id));
    }

    // ---- Talleres globales ----

    @GetMapping(path = "/talleres-globales", produces = "application/json")
    public ResponseEntity<Map<String, Object>> listarTalleres(
            Authentication auth,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "TODOS") String estado) {
        guard.exigir(principal(auth));
        return ok(talleres.listar(q, estado));
    }

    @GetMapping(path = "/talleres-globales/{id}", produces = "application/json")
    public ResponseEntity<Map<String, Object>> detalleTaller(Authentication auth, @PathVariable long id) {
        guard.exigir(principal(auth));
        return ok(talleres.detalle(id));
    }

    @PostMapping(path = "/talleres-globales", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> crearTaller(Authentication auth, @RequestBody Map<String, Object> body) {
        var p = guard.exigir(principal(auth));
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(talleres.crear(p.userId(), body));
    }

    @PutMapping(path = "/talleres-globales/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> editarTaller(
            Authentication auth, @PathVariable long id, @RequestBody Map<String, Object> body) {
        var p = guard.exigir(principal(auth));
        return ok(talleres.editar(p.userId(), id, body));
    }

    @PostMapping(path = "/talleres-globales/{id}/desactivar", produces = "application/json")
    public ResponseEntity<Map<String, Object>> desactivarTaller(Authentication auth, @PathVariable long id) {
        var p = guard.exigir(principal(auth));
        return ok(talleres.desactivar(p.userId(), id));
    }

    @PostMapping(path = "/talleres-globales/{id}/reactivar", produces = "application/json")
    public ResponseEntity<Map<String, Object>> reactivarTaller(Authentication auth, @PathVariable long id) {
        var p = guard.exigir(principal(auth));
        return ok(talleres.reactivar(p.userId(), id));
    }

    private static ResponseEntity<Map<String, Object>> ok(Map<String, Object> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static SessionAuthentication.Principal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof SessionAuthentication.Principal p)) {
            throw backend.catalogos.CatalogoException.forbidden("Sesión inválida.");
        }
        return p;
    }
}
