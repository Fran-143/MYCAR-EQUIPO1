package backend.mantenimiento.api;

import backend.mantenimiento.api.dto.ContratoRf17.ErrorResponse;
import backend.mantenimiento.model.MantenimientoNoDisponibleException;
import backend.mantenimiento.seguridad.SesionMantenimientoNoValidada;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes=MantenimientoController.class)
public class ErroresMantenimientoController {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex) {
        Map<String,String> campos=new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> campos.putIfAbsent(e.getField(),e.getDefaultMessage()));
        return respuesta(400,"DATOS_INVALIDOS","Revisa los datos del formulario",campos,false);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> formato(Exception ex) {
        return respuesta(400,"DATOS_INVALIDOS","Revisa el formato de fechas, números y campos requeridos",Map.of(),false);
    }
    @ExceptionHandler(SesionMantenimientoNoValidada.class)
    public ResponseEntity<ErrorResponse> sesion(SesionMantenimientoNoValidada ex) {
        return respuesta(401,"SESION_REQUERIDA",ex.getMessage(),Map.of(),false);
    }
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> autenticacion(AuthenticationException ex) {
        return respuesta(401,"SESION_REQUERIDA","Se requiere una sesión válida de MyCar",Map.of(),false);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> acceso(AccessDeniedException ex) {
        return respuesta(403,"ACCESO_DENEGADO","No tienes acceso a esta operación",Map.of(),false);
    }
    @ExceptionHandler(IntegracionRf17PendienteException.class)
    public ResponseEntity<ErrorResponse> integracion(IntegracionRf17PendienteException ex) {
        return respuesta(503,"INTEGRACION_PENDIENTE",ex.getMessage(),Map.of(),false);
    }
    @ExceptionHandler(MantenimientoNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> vehiculo(MantenimientoNoDisponibleException ex) {
        return respuesta(404,"VEHICULO_NO_DISPONIBLE",ex.getMessage(),Map.of(),false);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> dato(IllegalArgumentException ex) {
        String mensaje=ex.getMessage()==null ? "Revisa los datos del mantenimiento" : ex.getMessage();
        return respuesta(400,"DATOS_INVALIDOS",mensaje,Map.of(),false);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflicto(DataIntegrityViolationException ex) {
        return respuesta(409,"CONTEXTO_DESACTUALIZADO",
                "Los datos cambiaron. Actualiza las referencias antes de guardar",Map.of(),false);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> desconocido(Exception ex) {
        // Nunca exponer SQL, credenciales ni stack trace. Un fallo al confirmar puede ser incierto.
        return respuesta(500,"RESULTADO_DESCONOCIDO",
                "No se pudo confirmar el resultado. Verifica la operación antes de volver a guardar",Map.of(),true);
    }
    private ResponseEntity<ErrorResponse> respuesta(int estado,String codigo,String mensaje,
            Map<String,String> campos,boolean incierto) {
        return ResponseEntity.status(estado).body(new ErrorResponse(codigo,mensaje,campos,incierto));
    }
}
