package backend.historial.api;

import backend.historial.model.HistorialInconsistente;
import backend.historial.model.HistorialNoDisponible;
import backend.mantenimiento.api.IntegracionRf17PendienteException;
import backend.mantenimiento.seguridad.SesionMantenimientoNoValidada;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = HistorialMantenimientoController.class)
public class ErroresHistorialController {
    public record Error(String codigo, String mensaje) { }

    @ExceptionHandler({SesionMantenimientoNoValidada.class, AuthenticationException.class})
    ResponseEntity<Error> sesion(Exception ex) {
        return error(401, "SESION_REQUERIDA", "Inicia sesión para consultar tu historial");
    }

    @ExceptionHandler({HistorialNoDisponible.class, AccessDeniedException.class})
    ResponseEntity<Error> vehiculo(Exception ex) {
        return error(404, "VEHICULO_NO_DISPONIBLE", "El vehículo no está disponible");
    }

    @ExceptionHandler(IntegracionRf17PendienteException.class)
    ResponseEntity<Error> integracion(Exception ex) {
        return error(503, "INTEGRACION_PENDIENTE", "El historial todavía no está disponible");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> formato(Exception ex) {
        return error(400, "DATOS_INVALIDOS", "Revisa el vehículo seleccionado");
    }

    @ExceptionHandler(HistorialInconsistente.class)
    ResponseEntity<Error> inconsistencia(Exception ex) {
        return error(500, "DATOS_INCONSISTENTES", "No se pudo presentar el historial");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> tecnico(Exception ex) {
        return error(503, "CONSULTA_NO_DISPONIBLE", "No se pudo consultar el historial. Inténtalo nuevamente");
    }

    private ResponseEntity<Error> error(int estado, String codigo, String mensaje) {
        return ResponseEntity.status(estado).body(new Error(codigo, mensaje));
    }
}
