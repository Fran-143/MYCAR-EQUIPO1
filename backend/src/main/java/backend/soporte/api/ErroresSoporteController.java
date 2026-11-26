package backend.soporte.api;

import backend.soporte.model.ErrorSoporte;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = {"backend.soporte.api", "backend.admin.web"})
public class ErroresSoporteController {

    @ExceptionHandler(SesionVehiculosNoValidada.class)
    public ResponseEntity<Map<String, Object>> sesion(SesionVehiculosNoValidada error) {
        return cuerpo(HttpStatus.UNAUTHORIZED, "SESION_NO_VALIDA", "Inicia sesión para continuar.");
    }

    @ExceptionHandler(ErrorSoporte.class)
    public ResponseEntity<Map<String, Object>> funcional(ErrorSoporte error) {
        HttpStatus estado = switch (error.codigo()) {
            case "DATOS_INVALIDOS", "DOCUMENTO_DEMASIADO_GRANDE", "VIN_AUSENTE", "VIN_INVALIDO",
                 "VIN_SIN_CAMBIO", "TIVE_REQUERIDA" -> HttpStatus.BAD_REQUEST;
            case "VEHICULO_NO_ENCONTRADO", "SOLICITUD_NO_ENCONTRADA", "DOCUMENTO_NO_DISPONIBLE",
                 "DOCUMENTO_NO_ENCONTRADO" -> HttpStatus.NOT_FOUND;
            case "MFA_STEP_UP_REQUERIDO", "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "SOLICITUD_ABIERTA", "VIN_DUPLICADO", "CONFLICTO_IDEMPOTENCIA", "CONFLICTO_VERSION",
                 "OPERACION_EN_PROCESO", "CASO_CERRADO", "ESTADO_INVALIDO", "VIN_ACTUAL_CAMBIO",
                 "TIVE_INCONSISTENTE", "APROBACION_FALLIDA", "CONFLICTO" -> HttpStatus.CONFLICT;
            case "STORAGE_NO_CONFIGURADO", "STORAGE_NO_DISPONIBLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("codigo", error.codigo());
        body.put("mensaje", error.getMessage());
        if (!error.campos().isEmpty()) body.put("campos", error.campos());
        if (error.incierto()) body.put("incierto", true);
        return ResponseEntity.status(estado).body(body);
    }

    private static ResponseEntity<Map<String, Object>> cuerpo(HttpStatus s, String c, String m) {
        return ResponseEntity.status(s).body(Map.of("codigo", c, "mensaje", m));
    }
}
