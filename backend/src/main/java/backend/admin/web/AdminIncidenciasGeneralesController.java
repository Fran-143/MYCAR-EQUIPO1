package backend.admin.web;

import backend.autenticacion.AuthenticationStore;
import backend.autenticacion.SessionAuthentication;
import backend.soporte.api.SoporteController;
import backend.soporte.api.dto.ContratoSoporte;
import backend.soporte.model.ErrorSoporte;
import backend.soporte.service.SoporteService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/incidencias/generales")
public class AdminIncidenciasGeneralesController {
    private static final Duration MFA_RECIENTE = Duration.ofMinutes(10);

    private final SoporteService soporte;
    private final AuthenticationStore authStore;

    public AdminIncidenciasGeneralesController(SoporteService soporte, AuthenticationStore authStore) {
        this.soporte = soporte;
        this.authStore = authStore;
    }

    @GetMapping
    public List<ContratoSoporte.SolicitudResumenDto> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {
        return soporte.listarAdminGenerales(estado, q);
    }

    @GetMapping("/contadores")
    public ContratoSoporte.ContadoresIncidenciaDto contadores() {
        return soporte.contadoresGenerales();
    }

    @GetMapping("/{codigo}")
    public ContratoSoporte.SolicitudDetalleDto detalle(@PathVariable String codigo) {
        return soporte.detalleAdminGeneral(codigo);
    }

    @GetMapping("/{codigo}/evidencia")
    public ResponseEntity<byte[]> evidencia(@PathVariable String codigo) {
        ContratoSoporte.EvidenciaPayload payload = soporte.evidenciaAdminGeneral(codigo);
        return ResponseEntity.ok()
                .contentType(SoporteController.mediaTypeSeguro(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + SoporteController.nombreSeguro(payload.nombreOriginal()) + "\"")
                .body(payload.bytes());
    }

    @PostMapping("/{codigo}/aceptar")
    public ContratoSoporte.AccionResponse aceptar(
            Authentication auth,
            @PathVariable String codigo,
            @RequestBody(required = false) ContratoSoporte.ObservacionRequest body) {
        String obs = body == null ? null : body.observacion();
        return soporte.aceptarGeneral(adminId(auth), codigo, obs);
    }

    @PostMapping("/{codigo}/iniciar-atencion")
    public ContratoSoporte.AccionResponse iniciar(
            Authentication auth,
            @PathVariable String codigo,
            @RequestBody(required = false) ContratoSoporte.ObservacionRequest body) {
        String obs = body == null ? null : body.observacion();
        return soporte.iniciarAtencionGeneral(adminId(auth), codigo, obs);
    }

    @PostMapping("/{codigo}/terminar")
    public ContratoSoporte.AccionResponse terminar(
            Authentication auth,
            @PathVariable String codigo,
            @RequestBody ContratoSoporte.TerminarRequest body) {
        exigirMfaReciente(auth);
        String respuesta = body == null ? null : body.respuesta();
        return soporte.terminarGeneral(adminId(auth), codigo, respuesta);
    }

    @PostMapping("/{codigo}/rechazar")
    public ContratoSoporte.AccionResponse rechazar(
            Authentication auth,
            @PathVariable String codigo,
            @RequestBody ContratoSoporte.RechazarRequest body) {
        exigirMfaReciente(auth);
        String motivo = body == null ? null : body.motivo();
        return soporte.rechazarGeneral(adminId(auth), codigo, motivo);
    }

    private long adminId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof SessionAuthentication.Principal p) || !p.isAdmin()) {
            throw new ErrorSoporte("FORBIDDEN", "Se requiere administrador.");
        }
        return p.userId();
    }

    private void exigirMfaReciente(Authentication auth) {
        if (!(auth.getPrincipal() instanceof SessionAuthentication.Principal p)) {
            throw new ErrorSoporte("MFA_STEP_UP_REQUERIDO", "Confirma MFA para continuar.");
        }
        var session = authStore.findSession(p.sessionId())
                .orElseThrow(() -> new ErrorSoporte("MFA_STEP_UP_REQUERIDO", "Confirma MFA para continuar."));
        Instant verificado = session.mfaVerificadoUtc();
        Instant now = authStore.now();
        if (verificado == null || verificado.isBefore(now.minus(MFA_RECIENTE))) {
            throw new ErrorSoporte("MFA_STEP_UP_REQUERIDO",
                    "Debes confirmar MFA reciente (últimos 10 minutos) para resolver el caso.");
        }
    }
}
