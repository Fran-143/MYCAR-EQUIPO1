package backend.vehiculos.eliminacion;

import backend.vehiculos.eliminacion.EliminacionVehiculoModelos.ConfirmarEliminacionRequest;
import backend.vehiculos.eliminacion.EliminacionVehiculoModelos.ConfirmarEliminacionResponse;
import backend.vehiculos.eliminacion.EliminacionVehiculoModelos.PrepararEliminacionResponse;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehiculos/{idVehiculo}/eliminacion")
public class EliminacionVehiculoController {
    private final ObjectProvider<IdentidadVehiculos> identidades;
    private final EliminacionVehiculoService servicio;

    public EliminacionVehiculoController(
            ObjectProvider<IdentidadVehiculos> identidades,
            EliminacionVehiculoService servicio
    ) {
        this.identidades = identidades;
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<PrepararEliminacionResponse> preparar(
            Authentication autenticacion,
            @PathVariable long idVehiculo
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.preparar(usuario(autenticacion), idVehiculo));
    }

    @PostMapping
    public ResponseEntity<ConfirmarEliminacionResponse> confirmar(
            Authentication autenticacion,
            @PathVariable long idVehiculo,
            @RequestBody ConfirmarEliminacionRequest body
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.confirmar(usuario(autenticacion), idVehiculo, body));
    }

    private long usuario(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {
            throw new SesionVehiculosNoValidada();
        }
        IdentidadVehiculos identidad = identidades.getIfAvailable();
        if (identidad == null) {
            throw new SesionVehiculosNoValidada();
        }
        long id = identidad.exigirUsuarioAutenticado(autenticacion);
        if (id <= 0) {
            throw new SesionVehiculosNoValidada();
        }
        return id;
    }
}
