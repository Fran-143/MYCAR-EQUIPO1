package backend.cuentas.web;

import backend.cuentas.CuentaException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "backend.cuentas")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CuentaErrorHandler {
    @ExceptionHandler(CuentaException.class)
    public ResponseEntity<Map<String, Object>> handle(CuentaException ex) {
        int status = switch (ex.reason()) {
            case VALIDACION -> 400;
            case NO_ENCONTRADO -> 404;
            case CONFLICTO, ESTADO_INVALIDO -> 409;
            case FORBIDDEN, MFA_STEP_UP -> 403;
            case BLOQUEO_RECUPERACION -> 429;
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigo", ex.codigo());
        body.put("mensaje", ex.getMessage());
        body.put("erroresPorCampo", ex.fields());
        body.putAll(ex.extras());
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body);
    }
}
