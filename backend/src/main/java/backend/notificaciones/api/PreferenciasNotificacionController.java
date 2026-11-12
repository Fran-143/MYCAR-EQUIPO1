package backend.notificaciones.api;

import backend.alertas.api.SesionAlertasNoValidada;
import backend.alertas.seguridad.IdentidadAlertas;
import backend.notificaciones.api.dto.ContratoPreferenciasNotificacion;
import backend.notificaciones.service.PreferenciaNotificacionService;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferencias-notificacion")
@ConditionalOnProperty(prefix = "mycar.rf46", name = "api-habilitada", havingValue = "true", matchIfMissing = true)
public class PreferenciasNotificacionController {
    private final PreferenciaNotificacionService servicio;
    private final ObjectProvider<IdentidadAlertas> identidades;

    public PreferenciasNotificacionController(
            PreferenciaNotificacionService servicio, ObjectProvider<IdentidadAlertas> identidades) {
        this.servicio = servicio;
        this.identidades = identidades;
    }

    @GetMapping
    public ContratoPreferenciasNotificacion.PreferenciasResponse consultar(Authentication auth) {
        return servicio.consultar(usuario(auth));
    }

    @PutMapping
    public ContratoPreferenciasNotificacion.PreferenciasResponse guardar(
            Authentication auth, @RequestBody ContratoPreferenciasNotificacion.PreferenciasRequest body) {
        return servicio.guardar(usuario(auth), body);
    }

    /** EA04: actualizar solo permisoDispositivo. */
    @PatchMapping("/permiso-dispositivo")
    public ContratoPreferenciasNotificacion.PreferenciasResponse permiso(
            Authentication auth, @RequestBody Map<String, String> body) {
        String permiso = body == null ? null : body.get("permisoDispositivo");
        return servicio.actualizarPermisoDispositivo(usuario(auth), permiso);
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
