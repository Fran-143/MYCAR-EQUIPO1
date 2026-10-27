package backend.vehiculos.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorPlacaTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "A1B234",
            "Z9X000",
            "M0N999"
    })
    void aceptaPlacasConElFormatoAcordado(String placa) {
        assertTrue(ValidadorPlaca.esValida(placa));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "A1B-234",
            "a1b234",
            " A1B234",
            "A1B234 ",
            "A1B23",
            "A1B2345",
            "ABC123",
            "A1B23X",
            "A1B234\n"
    })
    void rechazaPlacasQueNoCumplenElFormatoExacto(String placa) {
        assertFalse(ValidadorPlaca.esValida(placa));
    }
}