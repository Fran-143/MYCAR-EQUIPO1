package backend.push.api;

import backend.alertas.api.SesionAlertasNoValidada;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Evita que excepciones de push caigan en {@code /error} + denyAll (403 vacío).
 */
@RestControllerAdvice(assignableTypes = DispositivoPushController.class)
public class ErroresDispositivoPushController {

    public record ErrorResponse(String codigo, String mensaje, boolean ok) {
    }

    @ExceptionHandler({SesionAlertasNoValidada.class, AuthenticationException.class})
    ResponseEntity<ErrorResponse> sesion() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("SESION_NO_VALIDA", "Inicia sesión para continuar.", false));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> noDisponible(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("FCM_NO_CONFIGURADO", ex.getMessage(), false));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> datos(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("DATOS_INVALIDOS", ex.getMessage(), false));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> status(ResponseStatusException ex) {
        String codigo = ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
                ? "FCM_NO_CONFIGURADO"
                : "ERROR_PUSH";
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(codigo, ex.getReason() == null ? ex.getMessage() : ex.getReason(), false));
    }
}
