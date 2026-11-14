package backend.talleres.api;

import backend.talleres.model.DatosTaller.RegistrarTallerGlobalRequest;
import backend.talleres.model.DatosTaller.TallerGlobal;
import backend.talleres.service.TallerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta de talleres oficiales (taller_global). Autenticación por secreto admin,
 * no por sesión de usuario final.
 */
@RestController
@RequestMapping("/api/v1/rf34/admin")
@ConditionalOnProperty(prefix = "mycar.rf34", name = "api-habilitada", havingValue = "true")
public class TalleresAdminController {
    private final TallerService servicio;

    public TalleresAdminController(TallerService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/talleres-globales")
    public ResponseEntity<TallerGlobal> registrarGlobal(@RequestBody RegistrarTallerGlobalRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.registrarGlobalAdmin(body));
    }
}
