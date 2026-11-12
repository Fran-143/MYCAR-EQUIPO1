package backend.talleres.api;

import backend.autenticacion.SessionAuthentication;
import backend.mantenimiento.seguridad.SesionMantenimientoNoValidada;
import backend.talleres.model.DatosTaller.*;
import backend.talleres.service.TallerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rf34")
@ConditionalOnProperty(prefix = "mycar.rf34", name = "api-habilitada", havingValue = "true")
public class TalleresController {
    private final TallerService servicio;

    public TalleresController(TallerService servicio) { this.servicio = servicio; }

    @GetMapping("/talleres")
    public ResponseEntity<ListaTalleres> listar(Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.listar(usuario(auth)));
    }

    @GetMapping("/talleres/{idTaller}")
    public ResponseEntity<Taller> detalle(Authentication auth, @PathVariable long idTaller) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.detalle(usuario(auth), idTaller));
    }

    @PostMapping("/talleres")
    public ResponseEntity<Taller> registrar(Authentication auth, @RequestBody RegistrarTallerRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.registrar(usuario(auth), body));
    }

    @PutMapping("/talleres/{idTaller}")
    public ResponseEntity<Taller> actualizar(
            Authentication auth, @PathVariable long idTaller, @RequestBody ActualizarTallerRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.actualizar(usuario(auth), idTaller, body));
    }

    @PostMapping("/talleres/{idTaller}/retirar")
    public ResponseEntity<Taller> retirar(
            Authentication auth, @PathVariable long idTaller, @RequestBody RetirarTallerRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.retirar(usuario(auth), idTaller, body));
    }

    @PostMapping("/talleres/{idTaller}/favorito")
    public ResponseEntity<Taller> marcarFavorito(Authentication auth, @PathVariable long idTaller) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.marcarFavorito(usuario(auth), idTaller));
    }

    @DeleteMapping("/talleres/{idTaller}/favorito")
    public ResponseEntity<Taller> desmarcarFavorito(Authentication auth, @PathVariable long idTaller) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.desmarcarFavorito(usuario(auth), idTaller));
    }

    private long usuario(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {
            throw new SesionMantenimientoNoValidada();
        }
        if (!(autenticacion.getPrincipal() instanceof SessionAuthentication.Principal principal)
                || principal.userId() <= 0) {
            throw new SesionMantenimientoNoValidada();
        }
        return principal.userId();
    }
}
