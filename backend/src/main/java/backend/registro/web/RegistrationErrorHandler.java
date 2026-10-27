package backend.registro.web;

import backend.registro.RegistrationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = RegistrationController.class)
public class RegistrationErrorHandler {
    public record RegistrationError(String codigo, String mensaje, Map<String, String> erroresPorCampo) { }

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<RegistrationError> registration(RegistrationException ex) {
        return switch (ex.reason()) {
            case INVALID_INPUT -> error(HttpStatus.BAD_REQUEST, "VALIDACION", ex.getMessage(), ex.fieldErrors());
            case EMAIL_UNAVAILABLE -> error(HttpStatus.CONFLICT, "CORREO_NO_DISPONIBLE", ex.getMessage(), Map.of());
            case REGISTRATION_UNAVAILABLE -> unavailable();
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RegistrationError> malformed() {
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", "JSON inválido o fecha de nacimiento inválida.", Map.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<RegistrationError> databaseUnavailable() { return unavailable(); }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RegistrationError> unexpected() {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "No se pudo completar el registro.", Map.of());
    }

    private ResponseEntity<RegistrationError> unavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "REGISTRO_NO_DISPONIBLE",
                "No se pudo completar el registro.", Map.of());
    }

    private ResponseEntity<RegistrationError> error(HttpStatus status, String code, String message,
            Map<String, String> fields) {
        return ResponseEntity.status(status).body(new RegistrationError(code, message, fields));
    }
}
