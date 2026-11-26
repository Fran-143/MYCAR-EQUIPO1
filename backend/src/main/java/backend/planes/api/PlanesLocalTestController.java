package backend.planes.api;

import backend.autenticacion.SessionAuthentication;
import backend.localtest.LocalTestGuard;
import backend.planes.api.dto.ContratoPlanes;
import backend.planes.model.ErrorPlanes;
import backend.planes.service.PlanesService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/local-test/pagos-prueba")
@ConditionalOnProperty(name = "mycar.local-test.enabled", havingValue = "true")
public class PlanesLocalTestController {
    private final PlanesService service;
    private final boolean enabled;
    private final String qaToken;

    public PlanesLocalTestController(
            PlanesService service,
            @Value("${mycar.local-test.enabled:false}") boolean enabled,
            @Value("${mycar.local-test.qa-token:mycar-local-qa}") String qaToken) {
        this.service = service;
        this.enabled = enabled;
        this.qaToken = qaToken;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<ContratoPlanes.OperacionPagoResponse> crear(
            @RequestHeader(value = "X-MyCar-Local-Test", required = false) String token,
            @AuthenticationPrincipal SessionAuthentication.Principal principal,
            @RequestBody ContratoPlanes.CrearPagoRequest body) {
        exigirToken(token);
        long id = exigirUsuario(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.crearOperacion(
                        id,
                        body == null ? null : body.idPlanCodigo(),
                        body == null ? null : body.proveedor(),
                        null));
    }

    @PostMapping(path = "/{referencia}/simular", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ContratoPlanes.OperacionPagoResponse> simular(
            @RequestHeader(value = "X-MyCar-Local-Test", required = false) String token,
            @AuthenticationPrincipal SessionAuthentication.Principal principal,
            @PathVariable String referencia,
            @RequestBody ContratoPlanes.SimularPagoRequest body) {
        exigirToken(token);
        long id = exigirUsuario(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.simular(id, referencia, body == null ? null : body.escenario()));
    }

    @GetMapping(path = "/{referencia}", produces = "application/json")
    public ResponseEntity<ContratoPlanes.OperacionPagoResponse> consultar(
            @RequestHeader(value = "X-MyCar-Local-Test", required = false) String token,
            @AuthenticationPrincipal SessionAuthentication.Principal principal,
            @PathVariable String referencia) {
        exigirToken(token);
        long id = exigirUsuario(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.consultarOperacion(id, referencia));
    }

    @PostMapping(path = "/forzar-vencimiento", produces = "application/json")
    public ResponseEntity<?> forzarVencimiento(
            @RequestHeader(value = "X-MyCar-Local-Test", required = false) String token,
            @AuthenticationPrincipal SessionAuthentication.Principal principal) {
        exigirToken(token);
        long id = exigirUsuario(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.forzarVencimientoLocal(id));
    }

    @PostMapping(path = "/procesar-vencimientos", produces = "application/json")
    public ResponseEntity<?> procesarVencimientos(
            @RequestHeader(value = "X-MyCar-Local-Test", required = false) String token) {
        exigirToken(token);
        int n = service.procesarVencimientosPendientes();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(java.util.Map.of("procesados", n));
    }

    private void exigirToken(String token) {
        LocalTestGuard.requireEnabled(enabled);
        if (qaToken == null || qaToken.isBlank() || token == null || !qaToken.equals(token)) {
            throw new ErrorPlanes("NO_AUTORIZADO", "Token de pruebas locales inválido.");
        }
    }

    private static long exigirUsuario(SessionAuthentication.Principal principal) {
        if (principal == null || principal.userId() <= 0) {
            throw new ErrorPlanes("NO_AUTORIZADO", "Sesión requerida.");
        }
        return principal.userId();
    }
}
