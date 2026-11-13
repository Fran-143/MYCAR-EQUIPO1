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
@RestControllerAdvice(assignableTypes = {VehiculosController.class, KilometrajeVehiculoController.class})
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
            case "PLAN_FREE_NO_DISPONIBLE", "LIMITE_FREE", "LIMITE_ACTIVOS", "PLACA_DUPLICADA",
                 "VIN_DUPLICADO", "VEHICULO_DUPLICADO", "SIN_CAMBIOS", "CONFLICTO_REGISTRO",
                 "CONFLICTO_IDEMPOTENCIA", "OPERACION_EN_PROCESO", "KM_OBSOLETO", "KM_NO_SUPERIOR",
                 "KM_INTERVALO_INVALIDO", "LECTURA_NO_ULTIMA", "REFERENCIA_INCOMPATIBLE",
                 "CAMPO_PROTEGIDO", "IDENTIFICADORES_BLOQUEADOS_TIVE",
                 "PRINCIPAL_REQUIERE_REEMPLAZO", "ULTIMO_ACTIVO", "YA_ES_PRINCIPAL",
                 "CANDIDATO_NO_ELEGIBLE", "ESTADO_SIN_CAMBIOS" -> HttpStatus.CONFLICT;
            case "VEHICULO_INACTIVO", "KILOMETRAJE_NO_DISPONIBLE", "CORRECCION_NO_DISPONIBLE",
                 "CORRECCION_YA_APLICADA" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        String mensaje = switch (error.codigo()) {
            case "DATOS_INVALIDOS" -> "Revisa los campos señalados.";
            case "FILTRO_INVALIDO" -> "Selecciona Todos, Activos o Inactivos.";
            case "CUENTA_NO_AUTORIZADA" -> "Tu cuenta no tiene acceso a esta operación.";
            case "OPERACION_NO_ENCONTRADA" -> "No se encontró una operación disponible para tu cuenta.";
            case "VEHICULO_NO_ENCONTRADO" -> "No se encontró el vehículo en tu cuenta.";
            case "VEHICULO_INACTIVO" -> "Esta operación solo está disponible para vehículos activos.";
            case "KILOMETRAJE_NO_DISPONIBLE" -> "El kilometraje vigente no está disponible temporalmente.";
            case "KM_OBSOLETO" -> "Los datos de lectura cambiaron. Vuelve a cargar el kilometraje actual.";
            case "KM_NO_SUPERIOR" -> "El nuevo kilometraje debe ser mayor al vigente.";
            case "KM_INTERVALO_INVALIDO" ->
                    "El kilometraje corregido debe ser mayor que la penúltima lectura y menor que la última.";
            case "LECTURA_NO_ULTIMA" -> "Solo puedes corregir la última lectura registrada.";
            case "CORRECCION_NO_DISPONIBLE" ->
                    "No hay un valor entero válido entre la penúltima y la última lectura.";
            case "CORRECCION_YA_APLICADA" ->
                    "Esta lectura ya fue corregida. Registra una nueva lectura si necesitas actualizar el kilometraje.";
            case "REFERENCIA_INCOMPATIBLE" ->
                    "La corrección contradice mantenimientos históricos del vehículo.";
            case "PLAN_FREE_NO_DISPONIBLE" -> "No hay un plan disponible para registrar el vehículo.";
            case "LIMITE_FREE" -> "Alcanzaste el límite de vehículos registrados de tu plan.";
            case "LIMITE_ACTIVOS" -> "Alcanzaste el límite de vehículos activos de tu plan.";
            case "PRINCIPAL_REQUIERE_REEMPLAZO" ->
                    "Antes de desactivar este vehículo, establece otro vehículo activo como principal.";
            case "ULTIMO_ACTIVO" ->
                    "Debes conservar al menos un vehículo activo. No puedes desactivar el único activo.";
            case "YA_ES_PRINCIPAL" -> "Este vehículo ya es el principal de tu cuenta.";
            case "CANDIDATO_NO_ELEGIBLE" ->
                    "Solo un vehículo activo propio puede establecerse como principal.";
            case "ESTADO_SIN_CAMBIOS" -> "El vehículo ya se encuentra en ese estado.";
            case "PLACA_DUPLICADA" -> "Esta placa ya se encuentra registrada en MyCar";
            case "VIN_DUPLICADO" -> "El VIN ya está registrado.";
            case "VEHICULO_DUPLICADO" -> "Ya tienes un vehículo registrado con exactamente los mismos datos.";
            case "SIN_CAMBIOS" -> "Ya registraste esta información. Modifica algún campo o vuelve sin guardar.";
            case "CAMPO_PROTEGIDO" -> "No puedes modificar este campo desde aquí.";
            case "IDENTIFICADORES_BLOQUEADOS_TIVE" ->
                    "VIN, código de motor y número de serie están protegidos por la TIVE registrada.";
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
