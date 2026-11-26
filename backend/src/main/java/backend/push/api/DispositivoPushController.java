package backend.push.api;

import backend.alertas.api.SesionAlertasNoValidada;
import backend.alertas.seguridad.IdentidadAlertas;
import backend.push.api.dto.ContratoPush;
import backend.push.service.DispositivoPushService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/dispositivos-push")
public class DispositivoPushController {
    private final DispositivoPushService servicio;
    private final ObjectProvider<IdentidadAlertas> identidades;

    public DispositivoPushController(
            DispositivoPushService servicio, ObjectProvider<IdentidadAlertas> identidades) {
        this.servicio = servicio;
        this.identidades = identidades;
    }

    @PostMapping("/registrar")
    public ContratoPush.OkResponse registrar(
            Authentication auth, @RequestBody ContratoPush.RegistrarTokenRequest body) {
        try {
            servicio.registrar(usuario(auth), body.idInstalacion(), body.proveedor(), body.token());
            return ContratoPush.OkResponse.si();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/desvincular")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincular(
            Authentication auth, @RequestBody ContratoPush.DesvincularTokenRequest body) {
        try {
            servicio.desvincular(usuario(auth), body.idInstalacion());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/probar")
    public ContratoPush.ProbarResponse probar(Authentication auth) {
        try {
            return servicio.probar(usuario(auth));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    private long usuario(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new SesionAlertasNoValidada();
        }
        var id = identidades.getIfAvailable();
        if (id == null) {
            throw new SesionAlertasNoValidada();
        }
        return id.exigirUsuario(auth);
    }
}
