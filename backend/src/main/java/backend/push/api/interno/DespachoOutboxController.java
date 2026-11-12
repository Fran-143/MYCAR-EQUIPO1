package backend.push.api.interno;

import backend.push.service.RecordatorioPushService;
import backend.push.service.RecordatorioPushService.ResultadoProgramado;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Despacho de recordatorios push (ventana ≥05:00 America/Lima).
 * Auth: {@code X-MyCar-Cron-Secret}.
 */
@RestController
@RequestMapping("/api/v1/interno/push")
public class DespachoOutboxController {
    private final RecordatorioPushService recordatorios;

    public DespachoOutboxController(RecordatorioPushService recordatorios) {
        this.recordatorios = recordatorios;
    }

    public record Respuesta(
            boolean ok,
            boolean omitidoEnCurso,
            int alertasEvaluadas,
            int encolados,
            int enviados,
            int omitidosRf48) {}

    @PostMapping("/despachar-recordatorios")
    public ResponseEntity<Respuesta> despachar() {
        ResultadoProgramado r = recordatorios.ejecutarVentanaProgramada();
        return ResponseEntity.ok(new Respuesta(
                true,
                r.omitidoEnCurso(),
                r.alertasEvaluadas(),
                r.encolados(),
                r.enviados(),
                r.omitidosRf48()));
    }
}
