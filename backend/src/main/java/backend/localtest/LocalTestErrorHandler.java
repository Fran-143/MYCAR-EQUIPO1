package backend.localtest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "backend.localtest")
@ConditionalOnProperty(name = "mycar.local-test.enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalTestErrorHandler {
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> state(IllegalStateException ex) {
        return ResponseEntity.status(403).body(Map.of(
                "codigo", "LOCAL_TEST_PROHIBIDO",
                "mensaje", ex.getMessage() == null ? "No permitido." : ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> arg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "codigo", "LOCAL_TEST_VALIDACION",
                "mensaje", ex.getMessage() == null ? "Datos inválidos." : ex.getMessage()));
    }
}
