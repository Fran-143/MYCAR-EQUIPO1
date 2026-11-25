package backend.planes.api;

import backend.planes.model.ErrorPlanes;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "backend.planes")
public class ErroresPlanesController {

    @ExceptionHandler(ErrorPlanes.class)
    public ResponseEntity<Map<String, Object>> funcional(ErrorPlanes error) {
        HttpStatus status = switch (error.codigo()) {
            case "NO_AUTORIZADO", "FORBIDDEN", "MFA_STEP_UP_REQUERIDO" -> HttpStatus.FORBIDDEN;
            case "NO_ENCONTRADO", "OPERACION_NO_ENCONTRADA", "USUARIO_NO_ENCONTRADO" -> HttpStatus.NOT_FOUND;
            case "CONFIGURACION_PENDIENTE", "PROVEEDOR_NO_CONFIGURADO", "SANDBOX_DESHABILITADO",
                 "PAGO_NO_CONFIRMADO", "PRUEBA_NO_ELEGIBLE" ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case "CONFLICTO", "CONFLICTO_IDEMPOTENCIA", "ESTADO_INVALIDO", "YA_PREMIUM",
                 "SOLICITUD_ABIERTA" -> HttpStatus.CONFLICT;
            case "OPERACION_EN_PROCESO" -> HttpStatus.ACCEPTED;
            default -> HttpStatus.BAD_REQUEST;
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigo", error.codigo());
        body.put("mensaje", error.getMessage());
        body.put("reintentable", error.reintentable());
        return ResponseEntity.status(status).body(body);
    }
}
