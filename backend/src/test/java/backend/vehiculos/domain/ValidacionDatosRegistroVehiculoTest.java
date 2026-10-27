package backend.vehiculos.domain;

import backend.vehiculos.dto.DatosRegistroVehiculo;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidacionDatosRegistroVehiculoTest {

    private static final int ANIO_ACTUAL = 2026;

    @Test
    void aceptaDatosBasicosValidos() {
        // ARRANGE
        var datos = datosPrueba(
                "Toyota", "Corolla", "Blanco",
                "A1B234", 2020, 50000
        );

        // ACT
        Set<String> errores =
                ValidadorVehiculo.validarDatosBasicos(datos, ANIO_ACTUAL);

        // ASSERT
        assertTrue(errores.isEmpty());
    }

    @Test
    void identificaTextosObligatoriosAusentesOVacios() {
        // ARRANGE
        var datos = datosPrueba(
                null, "   ", "",
                "A1B234", 2020, 50000
        );

        // ACT
        Set<String> errores =
                ValidadorVehiculo.validarDatosBasicos(datos, ANIO_ACTUAL);

        // ASSERT
        assertEquals(Set.of("marca", "modelo", "color"), errores);
    }

    @Test
    void rechazaTextosQueSuperanLasLongitudesDeLaBd() {
        // ARRANGE
        var datos = datosPrueba(
                "M".repeat(101),
                "X".repeat(101),
                "C".repeat(81),
                "A1B234", 2020, 50000
        );

        // ACT
        Set<String> errores =
                ValidadorVehiculo.validarDatosBasicos(datos, ANIO_ACTUAL);

        // ASSERT
        assertEquals(Set.of("marca", "modelo", "color"), errores);
    }

    @Test
    void incluyeLosErroresDePlacaAnioYKilometraje() {
        // ARRANGE
        var datos = datosPrueba(
                "Toyota", "Corolla", "Blanco",
                "A1B-234", 2027, 0
        );

        // ACT
        Set<String> errores =
                ValidadorVehiculo.validarDatosBasicos(datos, ANIO_ACTUAL);

        // ASSERT
        assertEquals(
                Set.of("placa", "anioFabricacion", "kilometrajeInicial"),
                errores
        );
    }

    private DatosRegistroVehiculo datosPrueba(
            String marca,
            String modelo,
            String color,
            String placa,
            Integer anio,
            Integer kilometraje
    ) {
        return new DatosRegistroVehiculo(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                marca,
                modelo,
                "PARTICULAR",
                placa,
                anio,
                color,
                1L,
                1L,
                kilometraje,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}