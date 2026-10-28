package backend.alertas.programacion.api;

import backend.alertas.programacion.model.ProgramacionAlerta.*;
import backend.alertas.programacion.model.ErrorProgramacion;
import backend.alertas.programacion.service.ProgramacionAlertaService;
import backend.autenticacion.SessionAuthentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Contrato RF22 implementado en namespace propio, para revisión del equipo; no altera los de RF17/RF18. */
@RestController
@RequestMapping("/api/v1/rf22")
@ConditionalOnProperty(prefix="mycar.rf22",name="api-habilitada",havingValue="true")
public class ProgramacionAlertaController {
    private final ProgramacionAlertaService servicio;
    public ProgramacionAlertaController(ProgramacionAlertaService servicio) { this.servicio=servicio; }
    @GetMapping("/contexto") public ResponseEntity<Contexto> contexto(Authentication a) {return ok(servicio.contexto(usuario(a)));}
    @GetMapping("/vehiculos/{id}/antecedentes")
    public ResponseEntity<Antecedentes> antecedentes(Authentication a,@PathVariable long id,@RequestParam long idTipoMantenimiento,@RequestParam(required=false) String servicioElegido) {
        return ok(servicio.antecedentes(usuario(a),id,idTipoMantenimiento,servicioElegido));
    }
    @PostMapping("/revisiones") public ResponseEntity<Revision> revisar(Authentication a,@RequestBody Configuracion c) {return ok(servicio.revisar(usuario(a),c));}
    @PostMapping("/alertas") public ResponseEntity<Resultado> confirmar(Authentication a,@RequestBody Solicitud s) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(servicio.confirmar(usuario(a),s));
    }
    @GetMapping("/operaciones/{id}") public ResponseEntity<EstadoOperacion> consultar(Authentication a,@PathVariable UUID id) {return ok(servicio.consultar(usuario(a),id));}
    private <T> ResponseEntity<T> ok(T body) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    private long usuario(Authentication a) {
        if(a==null || !a.isAuthenticated() || !(a.getPrincipal() instanceof SessionAuthentication.Principal p) || p.userId()<=0)
            throw new ErrorProgramacion("SESION_REQUERIDA");
        return ((SessionAuthentication.Principal)a.getPrincipal()).userId();
    }
}
