package backend.historial.api;

import backend.historial.model.DatosHistorial.*;
import backend.autenticacion.SessionAuthentication;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import backend.historial.service.HistorialMantenimientoService;
import backend.mantenimiento.seguridad.SesionMantenimientoNoValidada;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Rutas RF18 propuestas, separadas de las APIs compartidas y deshabilitadas por defecto. */
@RestController
@RequestMapping("/api/v1/rf18")
@ConditionalOnProperty(prefix = "mycar.rf18", name = "api-habilitada", havingValue = "true")
public class HistorialMantenimientoController {
    private final HistorialMantenimientoService servicio;

    public HistorialMantenimientoController(HistorialMantenimientoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/contexto")
    public ResponseEntity<Contexto> contexto(Authentication autenticacion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.contexto(usuario(autenticacion)));
    }

    @GetMapping("/vehiculos/{idVehiculo}/mantenimientos")
    public ResponseEntity<Resultado> consultar(Authentication autenticacion, @PathVariable long idVehiculo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.consultar(usuario(autenticacion), idVehiculo));
    }

    private long usuario(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) throw new SesionMantenimientoNoValidada();
        // Solo aceptar el principal tipado que emite el filtro de sesión real de Elsa.
        if (!(autenticacion.getPrincipal() instanceof SessionAuthentication.Principal principal)
                || principal.userId() <= 0) throw new SesionMantenimientoNoValidada();
        return principal.userId();
    }
}
