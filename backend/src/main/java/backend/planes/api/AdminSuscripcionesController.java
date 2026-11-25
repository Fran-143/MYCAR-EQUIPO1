package backend.planes.api;

import backend.autenticacion.SessionAuthentication;
import backend.cuentas.CuentaAdminService;
import backend.cuentas.CuentaException;
import backend.planes.api.dto.ContratoPlanes;
import backend.planes.model.ErrorPlanes;
import backend.planes.service.PlanesService;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/suscripciones")
public class AdminSuscripcionesController {
    private final PlanesService service;
    private final CuentaAdminService cuentas;

    public AdminSuscripcionesController(PlanesService service, CuentaAdminService cuentas) {
        this.service = service;
        this.cuentas = cuentas;
    }

    @GetMapping(value = "/resumen", produces = "application/json")
    public ResponseEntity<ContratoPlanes.AdminResumenResponse> resumen() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.resumenAdmin());
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<Map<String, Object>> listar(
            @RequestParam(required = false) String filtro,
            @RequestParam(required = false) String q) {
        List<ContratoPlanes.AdminSuscripcionItem> items = service.listarAdmin(filtro, q);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("items", items));
    }

    @GetMapping(value = "/solicitudes", produces = "application/json")
    public ResponseEntity<Map<String, Object>> listarSolicitudes(
            @RequestParam(required = false) String filtro,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("items", service.listarSolicitudesAdmin(filtro, q)));
    }

    @GetMapping(value = "/solicitudes/{codigo}", produces = "application/json")
    public ResponseEntity<ContratoPlanes.AdminSolicitudDetalleDto> detalleSolicitud(
            @PathVariable String codigo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.detalleSolicitudAdmin(codigo));
    }

    @PostMapping(value = "/solicitudes/{codigo}/aprobar", produces = "application/json")
    public ResponseEntity<ContratoPlanes.DecisionResponse> aprobar(
            Authentication auth,
            @PathVariable String codigo,
            @RequestBody(required = false) ContratoPlanes.DecisionRequest body) {
        var p = principal(auth);
        exigirMfaAdmin(p);
        String obs = body == null ? null : body.observacion();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.aprobarSolicitud(p.userId(), codigo, obs));
    }

    @PostMapping(value = "/solicitudes/{codigo}/rechazar", produces = "application/json")
    public ResponseEntity<ContratoPlanes.DecisionResponse> rechazar(
            Authentication auth,
            @PathVariable String codigo,
            @RequestBody ContratoPlanes.DecisionRequest body) {
        var p = principal(auth);
        exigirMfaAdmin(p);
        String motivo = body == null ? null : body.motivo();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.rechazarSolicitud(p.userId(), codigo, motivo));
    }

    @PostMapping(value = "/{idUsuario}/cancelar-premium", produces = "application/json")
    public ResponseEntity<ContratoPlanes.DecisionResponse> cancelarPremium(
            Authentication auth,
            @PathVariable long idUsuario,
            @RequestBody ContratoPlanes.DecisionRequest body) {
        var p = principal(auth);
        exigirMfaAdmin(p);
        String motivo = body == null ? null : body.motivo();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.cancelarPremiumActivo(p.userId(), idUsuario, motivo));
    }

    @GetMapping(value = "/{idUsuario}", produces = "application/json")
    public ResponseEntity<ContratoPlanes.AdminDetalleResponse> detalle(@PathVariable long idUsuario) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.detalleAdmin(idUsuario));
    }

    @PostMapping(value = "/operaciones/{referencia}/verificar", produces = "application/json")
    public ResponseEntity<ContratoPlanes.VerificarResponse> verificar(@PathVariable String referencia) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.verificarAdmin(referencia));
    }

    private static SessionAuthentication.Principal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof SessionAuthentication.Principal p) || !p.isAdmin()) {
            throw new ErrorPlanes("FORBIDDEN", "Se requiere administrador.");
        }
        return p;
    }

    private void exigirMfaAdmin(SessionAuthentication.Principal p) {
        try {
            cuentas.exigirAdminMfa(p);
        } catch (CuentaException e) {
            throw new ErrorPlanes(e.codigo(), e.getMessage());
        }
    }
}
