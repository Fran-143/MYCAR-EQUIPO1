package backend.registro.verificacion.web;

import backend.registro.RegistrationException;
import backend.registro.verificacion.VerificationService.Failure;
import backend.registro.web.RegistrationErrorHandler.RegistrationError;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Order(1)
@RestControllerAdvice(basePackageClasses = VerificationController.class)
public class VerificationErrorHandler {

    @ExceptionHandler(Failure.class)
    public ResponseEntity<VerificationController.ErrorBody> failure(Failure ex) {
        HttpStatus status = switch (ex.code()) {
            case "ORIGEN_NO_AUTORIZADO", "CREDENCIAL_OPERACION_INVALIDA" -> HttpStatus.UNAUTHORIZED;
            case "SOLICITUD_INVALIDA", "REVISION_DESACTUALIZADA", "PROCESO_NO_ABIERTO",
                 "PROCESO_EXPIRADO", "IDEMPOTENCIA_INCOMPATIBLE", "CORREO_NO_COINCIDE",
                 "EMISION_NO_ENCONTRADA", "EMISION_AJENA", "TIEMPO_PROCESO_INSUFICIENTE" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "LIMITE_TECNICO" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(new VerificationController.ErrorBody(ex.code(), "No se pudo procesar la verificación.", null));
    }

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<RegistrationError> registration(RegistrationException ex) {
        return switch (ex.reason()) {
            case INVALID_INPUT -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new RegistrationError("VALIDACION", ex.getMessage(), ex.fieldErrors()));
            case EMAIL_UNAVAILABLE -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new RegistrationError("CORREO_NO_DISPONIBLE", ex.getMessage(), Map.of()));
            case REGISTRATION_UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new RegistrationError("REGISTRO_NO_DISPONIBLE", ex.getMessage(), Map.of()));
        };
    }
}
