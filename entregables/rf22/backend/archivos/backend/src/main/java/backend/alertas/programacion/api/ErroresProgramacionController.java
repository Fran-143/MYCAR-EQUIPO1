package backend.alertas.programacion.api;

import backend.alertas.programacion.model.ErrorProgramacion;
import backend.alertas.programacion.model.ProgramacionAlerta.Revision;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=ProgramacionAlertaController.class)
public class ErroresProgramacionController {
    public record Error(String codigo,String mensaje,Revision revision) { }
    @ExceptionHandler(ErrorProgramacion.class)
    public ResponseEntity<Error> negocio(ErrorProgramacion e) {
        HttpStatus status=switch(e.codigo()) {
            case "SESION_REQUERIDA"->HttpStatus.UNAUTHORIZED;
            case "VEHICULO_NO_DISPONIBLE"->HttpStatus.NOT_FOUND;
            case "KM_NO_REGISTRADO"->HttpStatus.UNPROCESSABLE_ENTITY;
            default->HttpStatus.CONFLICT;
        };
        String mensaje=switch(e.codigo()) {
            case "REVISION_CAMBIO"->"Los datos cambiaron. Revisa el resumen actualizado y confirma nuevamente.";
            case "ALERTA_EQUIVALENTE"->"Ya existe una alerta activa con estos objetivos. Revisa tu programación.";
            case "KM_NO_REGISTRADO"->"No hay kilometraje registrado. Puedes programar por tiempo.";
            case "TIPO_NO_DISPONIBLE"->"El tipo ya no está disponible. Selecciona uno vigente.";
            case "REFERENCIA_NO_DISPONIBLE"->"La referencia cambió. Revisa o retira el antecedente.";
            case "CONFLICTO_IDEMPOTENCIA"->"La operación no coincide con la solicitud original.";
            default->"No se puede completar la operación. Revisa la sesión y los datos.";
        };
        return respuesta(status,e.codigo(),mensaje,e.revision());
    }
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Error> entrada(Exception e) {return respuesta(HttpStatus.BAD_REQUEST,"DATOS_INVALIDOS","Revisa los campos, unidades e intervalos positivos.",null);}
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Error> duplicado(Exception e) {return respuesta(HttpStatus.CONFLICT,"ALERTA_EQUIVALENTE","Otra solicitud ya programó una alerta equivalente.",null);}
    @ExceptionHandler({DataAccessException.class,IllegalStateException.class})
    public ResponseEntity<Error> tecnico(Exception e) {return respuesta(HttpStatus.SERVICE_UNAVAILABLE,"RESULTADO_POR_COMPROBAR","No se pudo confirmar el resultado. Comprueba la misma operación antes de reintentar.",null);}
    private ResponseEntity<Error> respuesta(HttpStatus status,String codigo,String mensaje,Revision revision) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new Error(codigo,mensaje,revision));
    }
}
