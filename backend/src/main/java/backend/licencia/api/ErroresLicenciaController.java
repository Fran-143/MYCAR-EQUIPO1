package backend.licencia.api;

import backend.documentos.ErrorAlmacenamiento;
import backend.licencia.model.ErrorLicencia;
import backend.soat.model.ErrorSoat;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LicenciaController.class)
public class ErroresLicenciaController {

    @ExceptionHandler(SesionVehiculosNoValidada.class)
    public ResponseEntity<Map<String, Object>> sesion(SesionVehiculosNoValidada error) {
        return respuesta(HttpStatus.UNAUTHORIZED, "SESION_NO_VALIDA", "Inicia sesión para continuar.");
    }

    @ExceptionHandler(ErrorLicencia.class)
    public ResponseEntity<Map<String, Object>> funcional(ErrorLicencia error) {
        HttpStatus estado = switch (error.codigo()) {
            case "DATOS_INVALIDOS", "FOTO_DEMASIADO_GRANDE", "SIN_CAMBIOS", "LICENCIA_GUARDAR_FALLIDO" -> HttpStatus.BAD_REQUEST;
            case "LICENCIA_NO_ENCONTRADA", "FOTO_NO_DISPONIBLE" -> HttpStatus.NOT_FOUND;
            case "LICENCIA_ACTUAL_EXISTE", "LICENCIA_NUMERO_DUPLICADO", "CONFLICTO_IDEMPOTENCIA", "CONFLICTO_VERSION",
                 "OPERACION_EN_PROCESO", "ALERTA_LICENCIA_DUPLICADA", "FOTO_NO_AUTORIZADA",
                 "RENOVACION_NO_DISPONIBLE" -> HttpStatus.CONFLICT;
            case "CIFRADO_NO_CONFIGURADO", "STORAGE_NO_CONFIGURADO", "STORAGE_NO_DISPONIBLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return respuesta(estado, error.codigo(), error.getMessage(), error.campos(), error.incierto());
    }

    @ExceptionHandler(ErrorSoat.class)
    public ResponseEntity<Map<String, Object>> cifrado(ErrorSoat error) {
        if ("CIFRADO_NO_CONFIGURADO".equals(error.codigo())) {
            return respuesta(HttpStatus.SERVICE_UNAVAILABLE, error.codigo(), error.getMessage());
        }
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, error.codigo(), error.getMessage());
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

    private static ResponseEntity<Map<String, Object>> respuesta(
            HttpStatus estado, String codigo, String mensaje) {
        return respuesta(estado, codigo, mensaje, Map.of(), false);
    }

    private static ResponseEntity<Map<String, Object>> respuesta(
            HttpStatus estado, String codigo, String mensaje, Map<String, String> campos, boolean incierto) {
        var cuerpo = new java.util.LinkedHashMap<String, Object>();
        cuerpo.put("codigo", codigo);
        cuerpo.put("mensaje", mensaje);
        if (!campos.isEmpty()) {
            cuerpo.put("campos", campos);
        }
        if (incierto) {
            cuerpo.put("incierto", true);
        }
        return ResponseEntity.status(estado).body(cuerpo);
    }
}