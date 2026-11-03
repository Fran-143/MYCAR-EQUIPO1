package backend.alertas.api;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.seguridad.IdentidadAlertas;
import backend.alertas.service.ProgramarAlertaService;
import backend.alertas.service.SincronizarAlertasService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rf22")
@ConditionalOnProperty(prefix = "mycar.rf22", name = "api-habilitada", havingValue = "true")
public class AlertasController {
    private final ProgramarAlertaService programar;
    private final SincronizarAlertasService sincronizar;
    private final ObjectProvider<IdentidadAlertas> identidades;

    public AlertasController(
            ProgramarAlertaService programar,
            SincronizarAlertasService sincronizar,
            ObjectProvider<IdentidadAlertas> identidades) {
        this.programar = programar;
        this.sincronizar = sincronizar;
        this.identidades = identidades;
    }

    @GetMapping("/contexto")
    public ContratoRf22.ContextoResponse contexto(Authentication auth, @RequestParam long idVehiculo) {
        return programar.contexto(usuario(auth), idVehiculo);
    }

    @GetMapping("/vehiculos/{idVehiculo}/alertas")
    public ContratoRf22.ListaResponse listar(Authentication auth, @PathVariable long idVehiculo) {
        return sincronizar.listarMisAlertas(usuario(auth), idVehiculo);
    }

    @PostMapping("/vehiculos/{idVehiculo}/alertas")
    @ResponseStatus(HttpStatus.CREATED)
    public ContratoRf22.ProgramarResponse programar(
            Authentication auth, @PathVariable long idVehiculo, @Valid @RequestBody ContratoRf22.ProgramarRequest body) {
        return programar.programar(usuario(auth), idVehiculo, body);
    }

    @GetMapping("/operaciones/{idOperacion}")
    public ContratoRf22.EstadoResponse operacion(Authentication auth, @PathVariable UUID idOperacion) {
        return programar.consultarOperacion(usuario(auth), idOperacion);
    }

    private long usuario(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new SesionAlertasNoValidada();
        }
        var id = identidades.getIfAvailable();
        if (id == null) throw new IntegracionAlertasPendienteException();
        long usuario = id.exigirUsuario(auth);
        if (usuario <= 0) throw new SesionAlertasNoValidada();
        return usuario;
    }
}
