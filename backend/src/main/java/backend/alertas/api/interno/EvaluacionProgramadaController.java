package backend.alertas.api.interno;

import backend.alertas.service.EvaluacionProgramadaAlertasService;
import backend.alertas.service.EvaluacionProgramadaAlertasService.Modo;
import backend.alertas.service.EvaluacionProgramadaAlertasService.Resultado;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disparo externo (Cloudflare Worker) de la evaluación RF23.
 * Auth: header {@code X-MyCar-Cron-Secret} (no JWT).
 */
@RestController
@RequestMapping("/api/v1/interno/alertas")
public class EvaluacionProgramadaController {
    private final EvaluacionProgramadaAlertasService servicio;

    public EvaluacionProgramadaController(EvaluacionProgramadaAlertasService servicio) {
        this.servicio = servicio;
    }

    public record Solicitud(String modo) {}

    public record Respuesta(
            boolean ok,
            boolean omitidoEnCurso,
            String modo,
            int vehiculos,
            int cambiosEstado,
            int erroresVehiculo) {}

    @PostMapping("/evaluar-programadas")
    public ResponseEntity<Respuesta> evaluar(@RequestBody(required = false) Solicitud body) {
        Modo modo = parseModo(body == null ? null : body.modo());
        Resultado r = servicio.evaluar(modo);
        // 200 si autenticado. omitidoEnCurso=true ⇒ otra ejecución concurrente (sin escrituras extra).
        return ResponseEntity.ok(new Respuesta(
                true,
                r.omitidoEnCurso(),
                r.modo(),
                r.vehiculos(),
                r.cambiosEstado(),
                r.erroresVehiculo()));
    }

    private static Modo parseModo(String raw) {
        if (raw == null || raw.isBlank()) {
            return Modo.COMPLETO;
        }
        try {
            return Modo.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Modo.COMPLETO;
        }
    }
}
