package backend.tive.service;

import backend.tive.model.ErrorTive;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Traduce violaciones de integridad de persistencia TIVE a errores de negocio
 * sin exponer nombres de tablas ni SQL al cliente.
 */
public final class IntegridadTive {
    private IntegridadTive() { }

    public static ErrorTive mapear(DataIntegrityViolationException ex) {
        String detalle = textoCausa(ex).toLowerCase(Locale.ROOT);

        if (contiene(detalle, "uq_tive_vehiculo", "tive_id_vehiculo_key")) {
            return new ErrorTive("TIVE_EXISTE", "Este vehículo ya tiene una TIVE registrada.");
        }
        if (contiene(detalle, "operacion_idempotente_tipo_operacion_check", "tipo_operacion_check")) {
            return new ErrorTive(
                    "TIVE_GUARDAR_FALLIDO",
                    "No pudimos registrar la operación. Inténtalo de nuevo más tarde.");
        }
        if (contiene(detalle, "operacion_idempotente", "duplicate key")
                && contiene(detalle, "id_operacion")) {
            return new ErrorTive(
                    "CONFLICTO_IDEMPOTENCIA",
                    "Esta operación ya fue registrada con otros datos.");
        }
        if (contiene(detalle, "numero_vin", "tive_numero_vin") && contiene(detalle, "check")) {
            return new ErrorTive(
                    "VIN_INVALIDO",
                    "El VIN del vehículo no cumple el formato requerido para TIVE.",
                    Set.of("numeroVin"));
        }
        if (contiene(detalle, "placa") && contiene(detalle, "check")) {
            return new ErrorTive(
                    "DATOS_INVALIDOS",
                    "La placa del vehículo no cumple el formato requerido para TIVE.",
                    Set.of("placa"));
        }
        if (contiene(detalle, "numero_motor") && contiene(detalle, "check")) {
            return new ErrorTive(
                    "DATOS_INVALIDOS",
                    "El número de motor no cumple el formato permitido.",
                    Set.of("numeroMotor"));
        }
        if (contiene(detalle, "numero_serie") && contiene(detalle, "check")) {
            return new ErrorTive(
                    "DATOS_INVALIDOS",
                    "El número de serie no cumple el formato permitido.",
                    Set.of("numeroSerie"));
        }
        if (contiene(detalle, "numero_titulo", "codigo_verificacion", "zona_registral", "sede_registral")
                && contiene(detalle, "check")) {
            return new ErrorTive(
                    "DATOS_INVALIDOS",
                    "Revisa los datos documentales de la TIVE.");
        }
        if (contiene(detalle, "foreign key", "fk_") || contiene(detalle, "violates foreign key")) {
            return new ErrorTive("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
        }
        if (contiene(detalle, "value too long", "character varying", "too long")) {
            return new ErrorTive(
                    "DATOS_INVALIDOS",
                    "Uno de los campos supera la longitud permitida.");
        }
        return new ErrorTive(
                "TIVE_GUARDAR_FALLIDO",
                "No pudimos guardar la TIVE por un problema interno. Inténtalo de nuevo.");
    }

    private static String textoCausa(DataIntegrityViolationException ex) {
        Throwable causa = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause() : ex;
        String mensaje = causa.getMessage();
        return mensaje == null ? "" : mensaje;
    }

    private static boolean contiene(String haystack, String... tokens) {
        for (String token : tokens) {
            if (haystack.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
