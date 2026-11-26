package backend.vehiculos.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RF08 — TDD de ValidadorPlaca (Arrange / Act / Assert). */
class ValidadorPlacaTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "A1B234",
            "Z9X000",
            "M0N999",
            "ABC123",
            "XYZ000",
            "QWE999"
    })
    void aceptaPlacasConElFormatoAcordado(String placa) {
        // Arrange: placa válida parametrizada (A1B234 o ABC123)

        // Act
        boolean ok = ValidadorPlaca.esValida(placa);

        // Assert
        assertTrue(ok);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "A1B-234",
            "ABC-123",
            "a1b234",
            "abc123",
            " A1B234",
            "A1B234 ",
            "A1B23",
            "A1B2345",
            "AB1234",
            "ABCD12",
            "A1B23X",
            "A1B234\n"
    })
    void rechazaPlacasQueNoCumplenElFormatoExacto(String placa) {
        // Arrange: placa inválida parametrizada

        // Act
        boolean ok = ValidadorPlaca.esValida(placa);

        // Assert
        assertFalse(ok);
    }
}
