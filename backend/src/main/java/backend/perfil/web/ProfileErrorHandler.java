package backend.perfil.web;

import backend.perfil.ProfileException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = ProfileController.class)
public class ProfileErrorHandler {
    public record ProfileError(String codigo, String mensaje, Map<String, String> erroresPorCampo) { }

    @ExceptionHandler(ProfileException.class)
    public ResponseEntity<ProfileError> profile(ProfileException ex) {
        return switch (ex.reason()) {
            case VALIDATION -> error(HttpStatus.BAD_REQUEST, "VALIDACION", ex.getMessage(), ex.fieldErrors());
            case NOT_FOUND -> error(HttpStatus.NOT_FOUND, "PERFIL_NO_ENCONTRADO", ex.getMessage(), Map.of());
            case UNAUTHORIZED_OPERATION -> error(HttpStatus.UNAUTHORIZED, "SESION_INVALIDA", ex.getMessage(), Map.of());
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProfileError> malformed() {
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", "JSON inválido o fecha de nacimiento inválida.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProfileError> unexpected() {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "No se pudo completar la operación.", Map.of());
    }

    private ResponseEntity<ProfileError> error(HttpStatus status, String code, String message,
            Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ProfileError(code, message, fields));
    }
}
