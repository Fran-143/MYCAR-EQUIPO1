package backend.localtest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/local-test")
@ConditionalOnProperty(name = "mycar.local-test.enabled", havingValue = "true")
public class LocalTestController {
    private final SimulatedMailInbox inbox;
    private final FixtureCuentasLocalService fixtures;
    private final boolean enabled;
    private final String qaToken;

    public LocalTestController(
            SimulatedMailInbox inbox,
            FixtureCuentasLocalService fixtures,
            @Value("${mycar.local-test.enabled:false}") boolean enabled,
            @Value("${mycar.local-test.qa-token:mycar-local-qa}") String qaToken) {
        this.inbox = inbox;
        this.fixtures = fixtures;
        this.enabled = enabled;
        this.qaToken = qaToken;
    }

    public record DestinatarioBody(String destinatario) { }
    public record FinalidadBody(String destinatario, String finalidad) { }

    @GetMapping(path = "/estado", produces = "application/json")
    public ResponseEntity<Map<String, Object>> estado(@RequestHeader(value = "X-MyCar-Local-Test", required = false) String token) {
        exigir(token);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("habilitado", enabled);
        out.put("simuladorCorreo", enabled);
        out.put("soloFixtures", true);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }

    @GetMapping(path = "/correos", produces = "application/json")
    public ResponseEntity<?> listar(
            @RequestHeader(value = "X-MyCar-Local-Test", required = false) String token,
            @RequestParam(required = false) String destinatario,
            @RequestParam(defaultValue = "50") int limit) {
        exigir(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("items", inbox.listar(destinatario, limit)));
    }

    @GetMapping(path = "/correos/{id}", produces = "application/json")
    public ResponseEntity<?> detalle(
            @RequestHeader(value = "X-MyCar-Local-Test", required = false) String token,
            @PathVariable long id) {
        exigir(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(inbox.detalle(id));
    }

    @PostMapping(path = "/correos/ultimo-otp", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> ultimoOtp(
            @RequestHeader(value = "X-MyCar-Local-Test", required = false) String token,
            @RequestBody FinalidadBody body) {
        exigir(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(inbox.ultimoPorFinalidad(
                        body == null ? null : body.destinatario(),
                        body == null ? null : body.finalidad()));
    }

    @PostMapping(path = "/fixtures/restablecer-usuarios", produces = "application/json")
    public ResponseEntity<?> restablecer(
            @RequestHeader(value = "X-MyCar-Local-Test", required = false) String token) {
        exigir(token);
        return ResponseEntity.ok(fixtures.restablecerUsuariosRf());
    }

    @PostMapping(path = "/fixtures/preparar-rf51-pendiente", produces = "application/json")
    public ResponseEntity<?> prepararRf51(
            @RequestHeader(value = "X-MyCar-Local-Test", required = false) String token) {
        exigir(token);
        return ResponseEntity.ok(fixtures.prepararPendienteRf51());
    }

    @PostMapping(path = "/fixtures/preparar-limite-eliminacion", produces = "application/json")
    public ResponseEntity<?> prepararLimite(
            @RequestHeader(value = "X-MyCar-Local-Test", required = false) String token) {
        exigir(token);
        return ResponseEntity.ok(fixtures.prepararLimiteEliminacion30d());
    }

    @PostMapping(path = "/fixtures/preparar-planes", produces = "application/json")
    public ResponseEntity<?> prepararPlanes(
            @RequestHeader(value = "X-MyCar-Local-Test", required = false) String token) {
        exigir(token);
        return ResponseEntity.ok(fixtures.prepararCuentasPlanes());
    }

    private void exigir(String token) {
        LocalTestGuard.requireEnabled(enabled);
        if (qaToken == null || qaToken.isBlank() || token == null || !qaToken.equals(token)) {
            throw new IllegalStateException("Token de pruebas locales inválido.");
        }
    }
}
