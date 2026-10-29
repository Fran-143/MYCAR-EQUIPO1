package backend.vehiculos.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorIdentificacionVehiculoTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "1HGCM82633A004352",
            "12345678901234567"
    })
    void aceptaVinOmitidoOConFormatoValido(String vin) {
        // ARRANGE: valor proporcionado por las anotaciones.

        // ACT
        boolean valido = ValidadorIdentificacionVehiculo.esVinValido(vin);

        // ASSERT
        assertTrue(valido);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "1HGCM82633A00435",
            "1HGCM82633A0043529",
            "1hgcm82633a004352",
            "IHGCM82633A004352",
            "OHGCM82633A004352",
            "QHGCM82633A004352",
            "1HGCM82633A00435-",
            "1HGCM82633A00435 "
    })
    void rechazaVinConFormatoInvalido(String vin) {
        // ARRANGE: VIN inválido proporcionado por las anotaciones.

        // ACT
        boolean valido = ValidadorIdentificacionVehiculo.esVinValido(vin);

        // ASSERT
        assertFalse(valido);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "MOTOR123",
            "AB-12",
            "AB 12",
            "ABCDEFGHIJKLMNO"
    })
    void aceptaMotorOmitidoOConFormatoValido(String motor) {
        // ARRANGE: valor proporcionado por las anotaciones.

        // ACT
        boolean valido =
                ValidadorIdentificacionVehiculo.esNumeroMotorValido(motor);

        // ASSERT
        assertTrue(valido);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "AB12",
            "ABCDEFGHIJKLMNOP",
            "abc12",
            "AB_12",
            "     ",
            "AB\t12",
            "AB\n12"
    })
    void rechazaMotorConFormatoInvalido(String motor) {
        // ARRANGE: número de motor inválido.

        // ACT
        boolean valido =
                ValidadorIdentificacionVehiculo.esNumeroMotorValido(motor);

        // ASSERT
        assertFalse(valido);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "ABC123",
            "SERIE123"
    })
    void aceptaSerieOmitidaOConFormatoValido(String serie) {
        // ARRANGE: valor proporcionado por las anotaciones.

        // ACT
        boolean valido =
                ValidadorIdentificacionVehiculo.esNumeroSerieValido(serie);

        // ASSERT
        assertTrue(valido);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "ABC12",
            "abc123",
            "ABC 123",
            "ABC-123",
            "ABC_123"
    })
    void rechazaSerieConFormatoInvalido(String serie) {
        // ARRANGE: número de serie inválido.

        // ACT
        boolean valido =
                ValidadorIdentificacionVehiculo.esNumeroSerieValido(serie);

        // ASSERT
        assertFalse(valido);
    }

    @Test
    void respetaElLimiteFisicoDelNumeroDeSerie() {
        // ARRANGE
        String serieMaxima = "A".repeat(80);
        String serieDemasiadoLarga = "A".repeat(81);

        // ACT
        boolean aceptaMaxima =
                ValidadorIdentificacionVehiculo.esNumeroSerieValido(
                        serieMaxima
                );

        boolean aceptaDemasiadoLarga =
                ValidadorIdentificacionVehiculo.esNumeroSerieValido(
                        serieDemasiadoLarga
                );

        // ASSERT
        assertTrue(aceptaMaxima);
        assertFalse(aceptaDemasiadoLarga);
    }
}