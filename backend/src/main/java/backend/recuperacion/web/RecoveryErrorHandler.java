package backend.recuperacion.web;

import backend.cuentas.CuentaException;
import backend.recuperacion.RecoveryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = RecoveryController.class)
public class RecoveryErrorHandler {
    public record RecoveryError(String codigo, String mensaje, Map<String, String> erroresPorCampo,
            Object bloqueadoHastaUtc, Object loginPermitido) {
        public RecoveryError(String codigo, String mensaje, Map<String, String> erroresPorCampo) {
            this(codigo, mensaje, erroresPorCampo, null, null);
        }
    }

    @ExceptionHandler(CuentaException.class)
    public ResponseEntity<Map<String, Object>> handleCuenta(CuentaException ex) {
        if (ex.reason() == CuentaException.Reason.BLOQUEO_RECUPERACION) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("codigo", "BLOQUEO_RECUPERACION");
            body.put("mensaje", ex.getMessage());
            body.put("erroresPorCampo", Map.of());
            body.putAll(ex.extras());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigo", ex.codigo());
        body.put("mensaje", ex.getMessage());
        body.put("erroresPorCampo", ex.fields());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(RecoveryException.class)
    public ResponseEntity<RecoveryError> handle(RecoveryException ex) {
        return switch (ex.reason()) {
            case VALIDATION -> error(HttpStatus.BAD_REQUEST, "VALIDACION", ex.getMessage(), ex.fieldErrors());
            case INVALID_CODE -> error(HttpStatus.UNPROCESSABLE_ENTITY, "CODIGO_INVALIDO", ex.getMessage(), ex.fieldErrors());
            case EXPIRED -> error(HttpStatus.UNPROCESSABLE_ENTITY, "CODIGO_EXPIRADO", ex.getMessage(), ex.fieldErrors());
            case UNAVAILABLE -> error(HttpStatus.SERVICE_UNAVAILABLE, "RECUPERACION_NO_DISPONIBLE", ex.getMessage(), Map.of());
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RecoveryError> malformed() {
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", "JSON inválido.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RecoveryError> unexpected() {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "No se pudo completar la recuperación.", Map.of());
    }

    private ResponseEntity<RecoveryError> error(HttpStatus status, String code, String message,
            Map<String, String> fields) {
        return ResponseEntity.status(status).body(new RecoveryError(code, message, fields));
    }
}
