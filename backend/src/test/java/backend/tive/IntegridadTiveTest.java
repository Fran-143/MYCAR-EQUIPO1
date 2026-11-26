package backend.tive;

import backend.tive.model.ErrorTive;
import backend.tive.service.IntegridadTive;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;

class IntegridadTiveTest {

    @Test
    void duplicadoVehiculoEsTiveExiste() {
        ErrorTive err = IntegridadTive.mapear(ex(
                "ERROR: duplicate key value violates unique constraint \"uq_tive_vehiculo\""));
        assertEquals("TIVE_EXISTE", err.codigo());
        assertEquals(409, httpDe(err.codigo()));
    }

    @Test
    void checkVinNoSeEnmascaraComoTiveExiste() {
        ErrorTive err = IntegridadTive.mapear(ex(
                "ERROR: new row for relation \"tive\" violates check constraint \"tive_numero_vin_check\""));
        assertEquals("VIN_INVALIDO", err.codigo());
        assertTrue(err.campos().containsKey("numeroVin"));
    }

    @Test
    void checkMotorEsDatosInvalidos() {
        ErrorTive err = IntegridadTive.mapear(ex(
                "ERROR: new row for relation \"tive\" violates check constraint \"tive_numero_motor_check\""));
        assertEquals("DATOS_INVALIDOS", err.codigo());
        assertTrue(err.campos().containsKey("numeroMotor"));
    }

    @Test
    void checkPlacaEsDatosInvalidos() {
        ErrorTive err = IntegridadTive.mapear(ex(
                "ERROR: new row for relation \"tive\" violates check constraint \"tive_placa_check\""));
        assertEquals("DATOS_INVALIDOS", err.codigo());
        assertTrue(err.campos().containsKey("placa"));
    }

    @Test
    void falloGenericoEsGuardarFallido() {
        ErrorTive err = IntegridadTive.mapear(ex("ERROR: unexpected integrity problem"));
        assertEquals("TIVE_GUARDAR_FALLIDO", err.codigo());
    }

    @Test
    void motorElectricoNoSeEnmascaraComoInterno() {
        ErrorTive err = IntegridadTive.mapear(ex(
                "ERROR: Motor eléctrico: combustible/aceite/número motor no aplican"));
        assertEquals("DATOS_INVALIDOS", err.codigo());
        assertTrue(err.getMessage().toLowerCase().contains("eléctric"));
    }

    @Test
    void noExponeNombresDeConstraintAlUsuario() {
        ErrorTive err = IntegridadTive.mapear(ex(
                "ERROR: duplicate key value violates unique constraint \"uq_tive_vehiculo\""));
        assertFalse(err.getMessage().toLowerCase().contains("uq_tive"));
        assertFalse(err.getMessage().toLowerCase().contains("constraint"));
    }

    private static DataIntegrityViolationException ex(String mensaje) {
        return new DataIntegrityViolationException("wrap", new RuntimeException(mensaje));
    }

    /** Espejo mínimo de ErroresTiveController para el test unitario. */
    private static int httpDe(String codigo) {
        return switch (codigo) {
            case "DATOS_INVALIDOS", "VIN_INVALIDO", "VIN_AUSENTE" -> 400;
            case "TIVE_EXISTE", "CONFLICTO_IDEMPOTENCIA" -> 409;
            case "TIVE_GUARDAR_FALLIDO" -> 500;
            default -> 500;
        };
    }
}
