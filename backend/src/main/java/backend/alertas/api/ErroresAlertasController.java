package backend.alertas.api;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.model.ErrorAlertas;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AlertasController.class)
public class ErroresAlertasController {

    @ExceptionHandler(ErrorAlertas.class)
    ResponseEntity<ContratoRf22.ErrorResponse> error(ErrorAlertas ex) {
        HttpStatus status = switch (ex.codigo()) {
            case "VEHICULO_NO_ENCONTRADO", "KILOMETRAJE_NO_DISPONIBLE" -> HttpStatus.NOT_FOUND;
            case "ALERTA_EQUIVALENTE", "CONFLICTO_IDEMPOTENCIA", "BASES_CAMBIARON" -> HttpStatus.CONFLICT;
            case "DATOS_INVALIDOS", "OBJETIVO_NO_FUTURO" -> HttpStatus.BAD_REQUEST;
            case "OPERACION_EN_PROCESO" -> HttpStatus.ACCEPTED;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(new ContratoRf22.ErrorResponse(
                ex.codigo(), ex.getMessage(), ex.campos(), ex.incierto()));
    }

    @ExceptionHandler(SesionAlertasNoValidada.class)
    ResponseEntity<ContratoRf22.ErrorResponse> sesion() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ContratoRf22.ErrorResponse("SESION_NO_VALIDA", "Inicia sesión para continuar.", java.util.Map.of(), false));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ContratoRf22.ErrorResponse> autenticacion() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ContratoRf22.ErrorResponse("SESION_NO_VALIDA", "Inicia sesión para continuar.", java.util.Map.of(), false));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ContratoRf22.ErrorResponse> acceso() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                new ContratoRf22.ErrorResponse("ACCESO_DENEGADO", "No tienes acceso a esta operación.", java.util.Map.of(), false));
    }

    @ExceptionHandler(IntegracionAlertasPendienteException.class)
    ResponseEntity<ContratoRf22.ErrorResponse> integracion() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                new ContratoRf22.ErrorResponse("INTEGRACION_PENDIENTE",
                        "La API de alertas no está habilitada en este servidor.", java.util.Map.of(), false));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ContratoRf22.ErrorResponse> validacion(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(new ContratoRf22.ErrorResponse(
                "DATOS_INVALIDOS", "Revisa los datos de la alerta.", java.util.Map.of(), false));
    }
}
