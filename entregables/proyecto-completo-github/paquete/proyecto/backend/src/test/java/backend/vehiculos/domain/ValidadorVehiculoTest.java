package backend.vehiculos.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorVehiculoTest {

    private static final int ANIO_ACTUAL_DE_PRUEBA = 2026;

    @ParameterizedTest
    @ValueSource(ints = {1, 1800, 2025, 2026})
    void aceptaAnioPositivoQueNoSeaFuturo(int anio) {
        // ARRANGE
        int anioActual = ANIO_ACTUAL_DE_PRUEBA;

        // ACT
        boolean resultado =
                ValidadorVehiculo.esAnioValido(anio, anioActual);

        // ASSERT
        assertTrue(resultado);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 2027, 3000})
    void rechazaAnioAusenteNoPositivoOFuturo(Integer anio) {
        // ARRANGE
        int anioActual = ANIO_ACTUAL_DE_PRUEBA;

        // ACT
        boolean resultado =
                ValidadorVehiculo.esAnioValido(anio, anioActual);

        // ASSERT
        assertFalse(resultado);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100, 250000})
    void aceptaKilometrajeInicialPositivo(int kilometraje) {
        // ARRANGE: kilometraje proporcionado por las anotaciones.

        // ACT
        boolean resultado =
                ValidadorVehiculo.esKilometrajeInicialValido(kilometraje);

        // ASSERT
        assertTrue(resultado);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0})
    void rechazaKilometrajeInicialAusenteONoPositivo(
            Integer kilometraje
    ) {
        // ARRANGE: kilometraje proporcionado por las anotaciones.

        // ACT
        boolean resultado =
                ValidadorVehiculo.esKilometrajeInicialValido(kilometraje);

        // ASSERT
        assertFalse(resultado);
    }
}