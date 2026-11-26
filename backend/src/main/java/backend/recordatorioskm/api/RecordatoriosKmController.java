package backend.recordatorioskm.api;

import backend.alertas.api.SesionAlertasNoValidada;
import backend.alertas.seguridad.IdentidadAlertas;
import backend.recordatorioskm.api.dto.ContratoRecordatoriosKm;
import backend.recordatorioskm.service.RecordatorioKmService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recordatorios-km")
public class RecordatoriosKmController {
    private final RecordatorioKmService servicio;
    private final ObjectProvider<IdentidadAlertas> identidades;

    public RecordatoriosKmController(
            RecordatorioKmService servicio, ObjectProvider<IdentidadAlertas> identidades) {
        this.servicio = servicio;
        this.identidades = identidades;
    }

    @GetMapping
    public ContratoRecordatoriosKm.ConfiguracionResponse consultar(Authentication auth) {
        return servicio.consultar(usuario(auth));
    }

    @PatchMapping
    public ContratoRecordatoriosKm.ConfiguracionResponse estadoGlobal(
            Authentication auth, @RequestBody ContratoRecordatoriosKm.EstadoGlobalRequest body) {
        String estado = body == null ? null : body.estadoRecordatorios();
        return servicio.actualizarGlobal(usuario(auth), estado);
    }

    @PutMapping("/vehiculos/{idVehiculo}")
    public ContratoRecordatoriosKm.PreferenciaVehiculoResponse frecuencia(
            Authentication auth,
            @PathVariable long idVehiculo,
            @RequestBody ContratoRecordatoriosKm.FrecuenciaRequest body) {
        String frecuencia = body == null ? null : body.frecuencia();
        return servicio.guardarFrecuencia(usuario(auth), idVehiculo, frecuencia);
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
