package backend.historial.export;

import backend.mantenimiento.seguridad.SesionMantenimientoNoValidada;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ExportarHistorialPdfController.class)
public class ErroresExportarHistorialController {
    public record Error(String codigo, String mensaje) { }

    @ExceptionHandler({SesionMantenimientoNoValidada.class, AuthenticationException.class})
    ResponseEntity<Error> sesion(Exception ex) {
        return ResponseEntity.status(401)
                .body(new Error("SESION_REQUERIDA", "Inicia sesión para exportar el historial"));
    }

    @ExceptionHandler(ExportarHistorialError.class)
    ResponseEntity<Error> negocio(ExportarHistorialError ex) {
        int status = switch (ex.codigo()) {
            case "PREMIUM_REQUERIDO" -> 403;
            case "VEHICULO_NO_DISPONIBLE" -> 404;
            case "PDF_NO_GENERADO" -> 503;
            default -> 400;
        };
        return ResponseEntity.status(status).body(new Error(ex.codigo(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> tecnico(Exception ex) {
        return ResponseEntity.status(503)
                .body(new Error("EXPORTACION_NO_DISPONIBLE", "No se pudo exportar el historial. Inténtalo nuevamente"));
    }
}
