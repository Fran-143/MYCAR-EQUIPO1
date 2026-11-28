package backend.recordatorioskm.api.interno;

import backend.push.service.RecordatorioPushService;
import backend.recordatorioskm.service.EvaluacionRecordatoriosKmService;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disparo controlado del evaluador RF43 (mismo patrón que alertas/push internos).
 * Auth: X-MyCar-Cron-Secret. Separado de RF23: un fallo aquí no cancela alertas.
 * Tras emitir avisos KM, encola push (despacho FCM en ventana / worker), igual que RF23.
 */
@RestController
@RequestMapping("/api/v1/interno/recordatorios-km")
public class EvaluacionRecordatoriosKmController {
    private static final Logger log = LoggerFactory.getLogger(EvaluacionRecordatoriosKmController.class);

    private final EvaluacionRecordatoriosKmService servicio;
    private final ObjectProvider<RecordatorioPushService> pushRecordatorios;

    public EvaluacionRecordatoriosKmController(
            EvaluacionRecordatoriosKmService servicio,
            ObjectProvider<RecordatorioPushService> pushRecordatorios) {
        this.servicio = servicio;
        this.pushRecordatorios = pushRecordatorios;
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
        // Misma cadena que alertas: notif → outbox. El worker/cron despachan FCM a todos los tokens.
        if (emitidas > 0) {
            var push = pushRecordatorios.getIfAvailable();
            if (push != null) {
                try {
                    var r = push.encolarCandidatosTrasEvaluacion();
                    log.info(
                            "RF43→push encolado: evaluadas={} encolados={} omitRf48={}",
                            r.alertasEvaluadas(), r.encolados(), r.omitidosRf48());
                } catch (Exception ex) {
                    log.warn("RF43→push encolado falló (no revierte evaluación): {}", ex.toString());
                }
            }
        }
        return ResponseEntity.ok(new Respuesta(true, "COMPLETO", emitidas, emitidas, false));
    }
}
