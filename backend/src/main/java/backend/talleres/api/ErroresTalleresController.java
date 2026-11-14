package backend.talleres.api;

import backend.mantenimiento.seguridad.SesionMantenimientoNoValidada;
import backend.talleres.model.TallerAsociadoNoEditable;
import backend.talleres.model.TallerFavoritoFallido;
import backend.talleres.model.TallerNombreDuplicado;
import backend.talleres.model.TallerNoDisponible;
import backend.talleres.model.TallerTelefonoDuplicado;
import backend.talleres.model.TallerVersionConflicto;
import backend.talleres.model.TallerYaVinculado;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = TalleresController.class)
public class ErroresTalleresController {
    public record Error(String codigo, String mensaje) { }

    @ExceptionHandler({SesionMantenimientoNoValidada.class, AuthenticationException.class})
    ResponseEntity<Error> sesion(Exception ex) {
        return error(401, "SESION_REQUERIDA", "Inicia sesión para gestionar tus talleres");
    }

    @ExceptionHandler(TallerNoDisponible.class)
    ResponseEntity<Error> noDisponible(Exception ex) {
        return error(404, "TALLER_NO_DISPONIBLE", "Este taller ya no está disponible");
    }

    @ExceptionHandler(TallerNombreDuplicado.class)
    ResponseEntity<Error> nombreDuplicado(TallerNombreDuplicado ex) {
        return error(409, "NOMBRE_DUPLICADO", ex.getMessage());
    }

    @ExceptionHandler(TallerTelefonoDuplicado.class)
    ResponseEntity<Error> telefonoDuplicado(TallerTelefonoDuplicado ex) {
        return error(409, "TELEFONO_DUPLICADO", ex.getMessage());
    }

    @ExceptionHandler(TallerYaVinculado.class)
    ResponseEntity<Error> yaVinculado(TallerYaVinculado ex) {
        return error(409, "TALLER_YA_VINCULADO", ex.getMessage());
    }

    @ExceptionHandler(TallerAsociadoNoEditable.class)
    ResponseEntity<Error> asociadoRo(TallerAsociadoNoEditable ex) {
        return error(403, "TALLER_ASOCIADO_SOLO_LECTURA", ex.getMessage());
    }

    @ExceptionHandler(TallerFavoritoFallido.class)
    ResponseEntity<Error> favorito(TallerFavoritoFallido ex) {
        return error(503, "FAVORITO_NO_ACTUALIZADO", ex.getMessage());
    }

    @ExceptionHandler(TallerVersionConflicto.class)
    ResponseEntity<Error> conflicto(Exception ex) {
        return error(409, "TALLER_VERSION_CONFLICTO",
                "El taller fue modificado por otro proceso. Recarga e inténtalo de nuevo.");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> datos(Exception ex) {
        String msg = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Revisa los datos del taller" : ex.getMessage();
        return error(400, "DATOS_INVALIDOS", msg);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> tecnico(Exception ex) {
        return error(503, "TALLERES_NO_DISPONIBLE", "No se pudo completar la operación. Inténtalo nuevamente");
    }

    private ResponseEntity<Error> error(int status, String codigo, String mensaje) {
        return ResponseEntity.status(status).body(new Error(codigo, mensaje));
    }
}
