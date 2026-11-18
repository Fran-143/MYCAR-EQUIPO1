package backend.tive.api;

import backend.documentos.ErrorAlmacenamiento;
import backend.tive.model.ErrorTive;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TiveController.class)
public class ErroresTiveController {

    @ExceptionHandler(SesionVehiculosNoValidada.class)
    public ResponseEntity<Map<String, Object>> sesion(SesionVehiculosNoValidada error) {
        return respuesta(HttpStatus.UNAUTHORIZED, "SESION_NO_VALIDA", "Inicia sesión para continuar.");
    }

    @ExceptionHandler(ErrorTive.class)
    public ResponseEntity<Map<String, Object>> funcional(ErrorTive error) {
        HttpStatus estado = switch (error.codigo()) {
            case "DATOS_INVALIDOS", "DOCUMENTO_DEMASIADO_GRANDE", "SIN_CAMBIOS",
                 "TIVE_GUARDAR_FALLIDO", "VIN_AUSENTE", "VIN_INVALIDO" -> HttpStatus.BAD_REQUEST;
            case "VEHICULO_NO_ENCONTRADO", "TIVE_NO_ENCONTRADA", "DOCUMENTO_NO_DISPONIBLE",
                 "DOCUMENTO_NO_ENCONTRADO" -> HttpStatus.NOT_FOUND;
            case "VEHICULO_INACTIVO" -> HttpStatus.FORBIDDEN;
            case "TIVE_EXISTE", "VIN_DUPLICADO", "CONFLICTO_IDEMPOTENCIA", "CONFLICTO_VERSION",
                 "OPERACION_EN_PROCESO", "DOCUMENTO_NO_AUTORIZADO" -> HttpStatus.CONFLICT;
            case "STORAGE_NO_CONFIGURADO", "STORAGE_NO_DISPONIBLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return respuesta(estado, error.codigo(), error.getMessage(), error.campos(), error.incierto());
    }

    @ExceptionHandler(ErrorAlmacenamiento.class)
    public ResponseEntity<Map<String, Object>> storage(ErrorAlmacenamiento error) {
        HttpStatus estado = "STORAGE_NO_CONFIGURADO".equals(error.codigo())
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return respuesta(estado, error.codigo(), error.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> json(HttpMessageNotReadableException error) {
        return respuesta(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "Revisa el formato de la solicitud.");
    }

    private static ResponseEntity<Map<String, Object>> respuesta(HttpStatus estado, String codigo, String mensaje) {
        return respuesta(estado, codigo, mensaje, Map.of(), false);
    }

    private static ResponseEntity<Map<String, Object>> respuesta(
            HttpStatus estado, String codigo, String mensaje, Map<String, String> campos, boolean incierto) {
        var cuerpo = new java.util.LinkedHashMap<String, Object>();
        cuerpo.put("codigo", codigo);
        cuerpo.put("mensaje", mensaje);
        if (!campos.isEmpty()) cuerpo.put("campos", campos);
        if (incierto) cuerpo.put("incierto", true);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
