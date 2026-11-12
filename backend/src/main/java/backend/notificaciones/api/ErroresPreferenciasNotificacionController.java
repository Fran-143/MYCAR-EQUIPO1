package backend.notificaciones.api;

import backend.alertas.api.SesionAlertasNoValidada;
import backend.notificaciones.api.dto.ContratoPreferenciasNotificacion;
import backend.notificaciones.model.ErrorNotificaciones;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PreferenciasNotificacionController.class)
public class ErroresPreferenciasNotificacionController {

    @ExceptionHandler(ErrorNotificaciones.class)
    ResponseEntity<ContratoPreferenciasNotificacion.ErrorResponse> error(ErrorNotificaciones ex) {
        HttpStatus status = switch (ex.codigo()) {
            case "DATOS_INVALIDOS" -> HttpStatus.BAD_REQUEST;
            case "PREFERENCIAS_NO_DISPONIBLES" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(
                new ContratoPreferenciasNotificacion.ErrorResponse(ex.codigo(), ex.getMessage(), false));
    }

    @ExceptionHandler({SesionAlertasNoValidada.class, AuthenticationException.class})
    ResponseEntity<ContratoPreferenciasNotificacion.ErrorResponse> sesion() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ContratoPreferenciasNotificacion.ErrorResponse(
                        "SESION_NO_VALIDA", "Inicia sesión para continuar.", false));
    }
}
