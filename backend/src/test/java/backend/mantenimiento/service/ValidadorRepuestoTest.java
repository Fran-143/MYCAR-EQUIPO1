package backend.mantenimiento.service;

import backend.mantenimiento.model.DatosRepuesto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorRepuestoTest {

    private final ValidadorRepuesto validador =
            new ValidadorRepuesto();

    @Test
    void RF17_CP05_EA04_rechazaLineaDeRepuestoAusente() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> validador.validar(null));
        assertTrue(error.getMessage().contains("repuesto"));
    }

    @Test
    void RF17_CP04_EA03_aceptaRepuestoValido() {
        // ARRANGE
        var datos = new DatosRepuesto(
                "Filtro de aceite",
                "Bosch",
                1,
                new BigDecimal("35.00")
        );

        // ACT + ASSERT
        assertDoesNotThrow(() -> validador.validar(datos));
    }

    @Test
    void RF17_CP05_EA04_rechazaCantidadCero() {
        // ARRANGE
        var datos = new DatosRepuesto(
                "Filtro de aceite",
                "Bosch",
                0,
                new BigDecimal("35.00")
        );

        // ACT + ASSERT
        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP05_EA04_rechazaPrecioNegativo() {
        // ARRANGE
        var datos = new DatosRepuesto(
                "Filtro de aceite",
                "Bosch",
                1,
                new BigDecimal("-5.00")
        );

        // ACT + ASSERT
        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }
    @Test
    void RF17_CP05_EA04_rechazaNombreAusente() {
        // ARRANGE
        var datos = new DatosRepuesto(
                null, "Bosch", 1, new BigDecimal("35.00")
        );

        // ACT + ASSERT
        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP05_EA04_rechazaNombreConSoloEspacios() {
        // ARRANGE
        var datos = new DatosRepuesto(
                "   ", "Bosch", 1, new BigDecimal("35.00")
        );

        // ACT + ASSERT
        assertThrows(
                IllegalArgumentException.class,
                () -> validador.validar(datos)
        );
    }

    @Test
    void RF17_CP04_EA03_aceptaMarcaYPrecioAusentes() {
        // ARRANGE
        var datos = new DatosRepuesto(
                "Filtro de aceite", null, 1, null
        );

        // ACT + ASSERT
        assertDoesNotThrow(() -> validador.validar(datos));
    }

    @Test
    void RF17_CP04_EA03_aceptaPrecioCero() {
        // ARRANGE
        var datos = new DatosRepuesto(
                "Filtro de aceite", "Bosch", 1, BigDecimal.ZERO
        );

        // ACT + ASSERT
        assertDoesNotThrow(() -> validador.validar(datos));
    }
}
