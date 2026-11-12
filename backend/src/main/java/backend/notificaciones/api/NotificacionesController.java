package backend.notificaciones.api;

import backend.alertas.api.SesionAlertasNoValidada;
import backend.alertas.seguridad.IdentidadAlertas;
import backend.notificaciones.api.dto.ContratoNotificaciones;
import backend.notificaciones.service.NotificacionInternaService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificaciones")
@ConditionalOnProperty(prefix = "mycar.rf46", name = "api-habilitada", havingValue = "true", matchIfMissing = true)
public class NotificacionesController {
    private final NotificacionInternaService servicio;
    private final ObjectProvider<IdentidadAlertas> identidades;

    public NotificacionesController(
            NotificacionInternaService servicio, ObjectProvider<IdentidadAlertas> identidades) {
        this.servicio = servicio;
        this.identidades = identidades;
    }

    @GetMapping
    public ContratoNotificaciones.BandejaResponse bandeja(
            Authentication auth,
            @RequestParam(defaultValue = "PENDIENTES") String pestana,
            @RequestParam(defaultValue = "TODAS") String categoria,
            @RequestParam(defaultValue = "TODAS") String filtroLectura) {
        return servicio.consultarBandeja(usuario(auth), pestana, categoria, filtroLectura);
    }

    @PostMapping("/{idNotificacion}/leer")
    public ContratoNotificaciones.LeerResponse leer(
            Authentication auth, @PathVariable long idNotificacion) {
        return servicio.marcarLeida(usuario(auth), idNotificacion);
    }

    private long usuario(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new SesionAlertasNoValidada();
        }
        var id = identidades.getIfAvailable();
        if (id == null) {
            throw new SesionAlertasNoValidada();
        }
        long usuario = id.exigirUsuario(auth);
        if (usuario <= 0) {
            throw new SesionAlertasNoValidada();
        }
        return usuario;
    }
}
