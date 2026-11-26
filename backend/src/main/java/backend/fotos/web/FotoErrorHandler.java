package backend.fotos.web;

import backend.fotos.FotoException;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {FotoPerfilController.class, FotoVehiculoController.class})
public class FotoErrorHandler {
    public record FotoError(String codigo, String mensaje, Map<String, String> erroresPorCampo) { }

    @ExceptionHandler(FotoException.class)
    public ResponseEntity<FotoError> foto(FotoException ex) {
        return switch (ex.reason()) {
            case VALIDATION -> error(HttpStatus.BAD_REQUEST, ex.codigo(), ex.getMessage(), ex.fieldErrors());
            case NOT_FOUND -> error(HttpStatus.NOT_FOUND, ex.codigo(), ex.getMessage(), Map.of());
            case CONFLICT -> error(HttpStatus.CONFLICT, ex.codigo(), ex.getMessage(), Map.of());
            case FORBIDDEN -> error(HttpStatus.FORBIDDEN, ex.codigo(), ex.getMessage(), Map.of());
            case UNAUTHORIZED -> error(HttpStatus.UNAUTHORIZED, ex.codigo(), ex.getMessage(), Map.of());
            case STORAGE -> error(HttpStatus.SERVICE_UNAVAILABLE, ex.codigo(), ex.getMessage(), Map.of());
        };
    }

    @ExceptionHandler(ErrorRegistroVehiculo.class)
    public ResponseEntity<FotoError> vehiculo(ErrorRegistroVehiculo ex) {
        String codigo = ex.codigo() == null ? "ERROR" : ex.codigo();
        HttpStatus status = switch (codigo) {
            case "VEHICULO_NO_ENCONTRADO" -> HttpStatus.NOT_FOUND;
            case "VEHICULO_INACTIVO" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        String mensaje = switch (codigo) {
            case "VEHICULO_NO_ENCONTRADO" -> "No se encontró el vehículo en tu cuenta.";
            case "VEHICULO_INACTIVO" -> "Esta operación solo está disponible para vehículos activos.";
            default -> "No se pudo completar la operación.";
        };
        return error(status, codigo, mensaje, Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<FotoError> demasiadoGrande() {
        return error(HttpStatus.BAD_REQUEST, "FOTO_INVALIDA", "La imagen no puede superar 5 MiB.",
                Map.of("foto", "La imagen no puede superar 5 MiB."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<FotoError> unexpected() {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "No se pudo completar la operación.", Map.of());
    }

    private ResponseEntity<FotoError> error(HttpStatus status, String code, String message,
            Map<String, String> fields) {
        return ResponseEntity.status(status).body(new FotoError(code, message, fields));
    }
}
