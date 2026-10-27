package backend.mantenimiento.api;

import backend.mantenimiento.api.dto.ContratoRf17.*;
import backend.mantenimiento.seguridad.*;
import backend.mantenimiento.service.MantenimientoHttpService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Rutas propuestas: ausentes por defecto, hasta confirmar integración con el equipo. */
@RestController
@RequestMapping("/api/v1/rf17")
@ConditionalOnProperty(prefix="mycar.rf17",name="api-habilitada",havingValue="true")
public class MantenimientoController {
    private final MantenimientoHttpService servicio;
    private final ObjectProvider<IdentidadMantenimiento> identidades;
    public MantenimientoController(MantenimientoHttpService servicio,
            ObjectProvider<IdentidadMantenimiento> identidades) {
        this.servicio=servicio; this.identidades=identidades;
    }
    @GetMapping("/contexto")
    public ContextoResponse contexto(Authentication autenticacion) {
        return servicio.contexto(usuario(autenticacion));
    }
    @GetMapping("/vehiculos/{idVehiculo}/lecturas")
    public List<LecturaDto> lecturas(Authentication autenticacion,
            @PathVariable long idVehiculo,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fechaServicio) {
        return servicio.lecturas(usuario(autenticacion),idVehiculo,fechaServicio);
    }
    @PostMapping("/vehiculos/{idVehiculo}/mantenimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroResponse registrar(Authentication autenticacion,
            @PathVariable long idVehiculo,@Valid @RequestBody RegistroRequest solicitud) {
        return servicio.registrar(usuario(autenticacion),idVehiculo,solicitud);
    }
    @GetMapping("/operaciones/{idOperacion}")
    public EstadoResponse consultar(Authentication autenticacion,@PathVariable UUID idOperacion) {
        return servicio.consultar(usuario(autenticacion),idOperacion);
    }
    private long usuario(Authentication autenticacion) {
        if (autenticacion==null || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {
            throw new SesionMantenimientoNoValidada();
        }
        var identidad=identidades.getIfAvailable();
        if (identidad==null) throw new IntegracionRf17PendienteException();
        long usuario=identidad.exigirUsuarioAutenticado(autenticacion);
        if (usuario<=0) throw new SesionMantenimientoNoValidada();
        return usuario;
    }
}
