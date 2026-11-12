package backend.notificaciones.api;

import backend.alertas.api.SesionAlertasNoValidada;
import backend.notificaciones.api.dto.ContratoNotificaciones;
import backend.notificaciones.model.ErrorNotificaciones;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = NotificacionesController.class)
public class ErroresNotificacionesController {

    @ExceptionHandler(ErrorNotificaciones.class)
    ResponseEntity<ContratoNotificaciones.ErrorResponse> error(ErrorNotificaciones ex) {
        HttpStatus status = switch (ex.codigo()) {
            case "NOTIFICACION_NO_ENCONTRADA" -> HttpStatus.NOT_FOUND;
            case "DATOS_INVALIDOS" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(
                new ContratoNotificaciones.ErrorResponse(ex.codigo(), ex.getMessage(), false));
    }

    @ExceptionHandler({SesionAlertasNoValidada.class, AuthenticationException.class})
    ResponseEntity<ContratoNotificaciones.ErrorResponse> sesion() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ContratoNotificaciones.ErrorResponse(
                        "SESION_NO_VALIDA", "Inicia sesión para continuar.", false));
    }
}
