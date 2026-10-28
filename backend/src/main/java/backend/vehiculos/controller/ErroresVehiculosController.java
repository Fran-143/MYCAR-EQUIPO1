package backend.vehiculos.controller;

import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;

/** Alcance exclusivo de este controlador; no altera los módulos de otros integrantes. */
@RestControllerAdvice(assignableTypes = VehiculosController.class)
public class ErroresVehiculosController {
    @ExceptionHandler(SesionVehiculosNoValidada.class)
    public ResponseEntity<ErrorApiVehiculos> sesion(SesionVehiculosNoValidada error) {
        return respuesta(HttpStatus.UNAUTHORIZED, "SESION_NO_VALIDA", "Inicia sesión para continuar.", Set.of());
    }

    @ExceptionHandler(ErrorRegistroVehiculo.class)
    public ResponseEntity<ErrorApiVehiculos> funcional(ErrorRegistroVehiculo error) {
        HttpStatus estado = switch (error.codigo()) {
            case "DATOS_INVALIDOS", "FILTRO_INVALIDO" -> HttpStatus.BAD_REQUEST;
            case "CUENTA_NO_AUTORIZADA" -> HttpStatus.FORBIDDEN;
            case "OPERACION_NO_ENCONTRADA", "VEHICULO_NO_ENCONTRADO" -> HttpStatus.NOT_FOUND;
            case "PLAN_FREE_NO_DISPONIBLE", "LIMITE_FREE", "PLACA_DUPLICADA",
                 "VIN_DUPLICADO", "CONFLICTO_REGISTRO", "CONFLICTO_IDEMPOTENCIA",
                 "OPERACION_EN_PROCESO" -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        String mensaje = switch (error.codigo()) {
            case "DATOS_INVALIDOS" -> "Revisa los campos señalados.";
            case "FILTRO_INVALIDO" -> "Selecciona Todos, Activos o Inactivos.";
            case "CUENTA_NO_AUTORIZADA" -> "Tu cuenta no tiene acceso a esta operación.";
            case "OPERACION_NO_ENCONTRADA" -> "No se encontró una operación disponible para tu cuenta.";
            case "VEHICULO_NO_ENCONTRADO" -> "No se encontró el vehículo en tu cuenta.";
            case "PLAN_FREE_NO_DISPONIBLE" -> "No hay un plan Free disponible para registrar el vehículo.";
            case "LIMITE_FREE" -> "El plan Free permite un vehículo registrado, incluyendo inactivos.";
            case "PLACA_DUPLICADA" -> "La placa ya está registrada.";
            case "VIN_DUPLICADO" -> "El VIN ya está registrado.";
            case "CONFLICTO_REGISTRO" -> "Los datos entraron en conflicto con otro registro.";
            case "CONFLICTO_IDEMPOTENCIA" -> "La operación no coincide con la solicitud original.";
            case "OPERACION_EN_PROCESO" -> "El resultado todavía está pendiente de confirmación.";
            default -> "No se pudo completar la operación.";
        };
        String codigo = estado.is5xxServerError() ? "ERROR_INTERNO" : error.codigo();
        return respuesta(estado, codigo, mensaje,
                estado.is5xxServerError() ? Set.of() : error.campos());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorApiVehiculos> formato(Exception error) {
        return respuesta(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA",
                "Revisa el formato de la solicitud.", Set.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorApiVehiculos> tipoContenido(HttpMediaTypeNotSupportedException error) {
        return respuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FORMATO_NO_ADMITIDO",
                "Envía la solicitud en formato JSON.", Set.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorApiVehiculos> datos(DataAccessException error) {
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE",
                "El servicio no está disponible. Inténtalo nuevamente.", Set.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorApiVehiculos> inesperado(Exception error) {
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "No se pudo completar la operación. Inténtalo nuevamente.", Set.of());
    }

    private ResponseEntity<ErrorApiVehiculos> respuesta(
            HttpStatus estado, String codigo, String mensaje, Set<String> campos
    ) {
        return ResponseEntity.status(estado).body(new ErrorApiVehiculos(codigo, mensaje, campos));
    }

    public record ErrorApiVehiculos(String codigo, String mensaje, Set<String> campos) {
    }
}
