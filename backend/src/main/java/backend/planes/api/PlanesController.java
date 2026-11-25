package backend.planes.api;

import backend.autenticacion.SessionAuthentication;
import backend.planes.api.dto.ContratoPlanes;
import backend.planes.model.ErrorPlanes;
import backend.planes.service.PlanesService;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PlanesController {
    private final PlanesService service;

    public PlanesController(PlanesService service) {
        this.service = service;
    }

    @GetMapping(value = "/planes", produces = "application/json")
    public ResponseEntity<ContratoPlanes.CatalogoResponse> catalogo(
            @AuthenticationPrincipal SessionAuthentication.Principal principal) {
        exigir(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.catalogo());
    }

    @GetMapping(value = "/suscripciones/mia", produces = "application/json")
    public ResponseEntity<ContratoPlanes.MiSuscripcionResponse> mia(
            @AuthenticationPrincipal SessionAuthentication.Principal principal) {
        long id = exigir(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.miSuscripcion(id));
    }

    @GetMapping(value = "/suscripciones/mia/eventos", produces = "application/json")
    public ResponseEntity<?> eventos(
            @AuthenticationPrincipal SessionAuthentication.Principal principal) {
        long id = exigir(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("items", service.misEventos(id)));
    }

    @GetMapping(value = "/suscripciones/mia/solicitudes", produces = "application/json")
    public ResponseEntity<?> solicitudes(
            @AuthenticationPrincipal SessionAuthentication.Principal principal) {
        long id = exigir(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("items", service.misSolicitudes(id)));
    }

    @GetMapping(value = "/suscripciones/mia/solicitudes/{codigo}", produces = "application/json")
    public ResponseEntity<ContratoPlanes.SolicitudDetalleDto> solicitudDetalle(
            @AuthenticationPrincipal SessionAuthentication.Principal principal,
            @PathVariable String codigo) {
        long id = exigir(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.miSolicitudDetalle(id, codigo));
    }

    @PostMapping(value = "/suscripciones/solicitudes/prueba-gratuita", produces = "application/json")
    public ResponseEntity<ContratoPlanes.SolicitudDto> solicitarPrueba(
            @AuthenticationPrincipal SessionAuthentication.Principal principal) {
        long id = exigir(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.solicitarPruebaGratuita(id));
    }

    /** Pago simulado (sin cobro real): queda pendiente de aprobación administrativa. */
    @PostMapping(value = "/suscripciones/solicitudes/pago-simulado", produces = "application/json")
    public ResponseEntity<ContratoPlanes.SolicitudDto> solicitarPagoSimulado(
            @AuthenticationPrincipal SessionAuthentication.Principal principal) {
        long id = exigir(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.solicitarPagoSimulado(id));
    }

    private static long exigir(SessionAuthentication.Principal principal) {
        if (principal == null || principal.userId() <= 0) {
            throw new ErrorPlanes("NO_AUTORIZADO", "Sesión requerida.");
        }
        return principal.userId();
    }
}
