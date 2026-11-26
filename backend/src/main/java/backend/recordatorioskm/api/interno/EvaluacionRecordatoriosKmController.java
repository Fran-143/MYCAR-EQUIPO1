package backend.recordatorioskm.api.interno;

import backend.recordatorioskm.service.EvaluacionRecordatoriosKmService;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disparo controlado del evaluador RF43 (mismo patrón que alertas/push internos).
 * Auth: X-MyCar-Cron-Secret. Separado de RF23: un fallo aquí no cancela alertas.
 */
@RestController
@RequestMapping("/api/v1/interno/recordatorios-km")
public class EvaluacionRecordatoriosKmController {
    private final EvaluacionRecordatoriosKmService servicio;

    public EvaluacionRecordatoriosKmController(EvaluacionRecordatoriosKmService servicio) {
        this.servicio = servicio;
    }

    public record Solicitud(String modo) {}

    public record Respuesta(
            boolean ok,
            String modo,
            int candidatos,
            int notificacionesEmitidas,
            boolean simulado) {}

    @PostMapping("/evaluar")
    public ResponseEntity<Respuesta> evaluar(@RequestBody(required = false) Map<String, Object> body) {
        String modo = "COMPLETO";
        if (body != null && body.get("modo") != null) {
            modo = String.valueOf(body.get("modo")).trim().toUpperCase(Locale.ROOT);
        }
        boolean simular = !"COMPLETO".equals(modo);
        if (simular) {
            int cand = servicio.contarCandidatos();
            return ResponseEntity.ok(new Respuesta(true, "SIMULAR", cand, 0, true));
        }
        int emitidas = servicio.evaluarPendientes();
        return ResponseEntity.ok(new Respuesta(true, "COMPLETO", emitidas, emitidas, false));
    }
}
