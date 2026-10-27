package backend.vehiculos.domain;

import backend.vehiculos.dto.DatosCatalogosVehiculo;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidacionMotorVehiculoTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("casosMotor")
    void validaReglasDelMotor(
            String escenario,
            DatosRegistroVehiculo datos,
            Set<String> erroresEsperados
    ) {
        // ARRANGE
        var catalogos = catalogosPrueba();

        // ACT
        Set<String> errores =
                ValidadorVehiculo.validarMotor(datos, catalogos);

        // ASSERT
        assertEquals(erroresEsperados, errores);
    }

    private static Stream<Arguments> casosMotor() {
        return Stream.of(
                Arguments.of(
                        "Eléctrico sin campos incompatibles",
                        datosPrueba(20L, null, null, null, null),
                        Set.of()
                ),
                Arguments.of(
                        "Eléctrico rechaza combustible, aceite y número de motor",
                        datosPrueba(20L, 1L, 1L, "M123", null),
                        Set.of("idCombustible", "idTipoAceite", "numeroMotor")
                ),
                Arguments.of(
                        "Motor no eléctrico permite aceite opcional",
                        datosPrueba(10L, 1L, null, null, null),
                        Set.of()
                ),
                Arguments.of(
                        "Motor no eléctrico requiere combustible",
                        datosPrueba(10L, null, null, null, null),
                        Set.of("idCombustible")
                ),
                Arguments.of(
                        "Motor OTRO requiere especificación",
                        datosPrueba(30L, 1L, null, null, null),
                        Set.of("tipoMotorOtro")
                ),
                Arguments.of(
                        "Combustible OTRO requiere especificación",
                        datosPrueba(10L, 2L, null, null, null),
                        Set.of("combustibleOtro")
                ),
                Arguments.of(
                        "Aceite OTRO requiere especificación",
                        datosPrueba(10L, 1L, 2L, null, null),
                        Set.of("tipoAceiteOtro")
                )
        );
    }

    private static DatosCatalogosVehiculo catalogosPrueba() {
        return new DatosCatalogosVehiculo(
                List.of(
                        new DatosCatalogosVehiculo.UsoVehiculo(
                                "PARTICULAR", "Particular"
                        )
                ),
                List.of(
                        new DatosCatalogosVehiculo.TipoMotor(
                                10L, "COMBUSTION", "Combustión", false
                        ),
                        new DatosCatalogosVehiculo.TipoMotor(
                                20L, "ELECTRICO", "Eléctrico", true
                        ),
                        new DatosCatalogosVehiculo.TipoMotor(
                                30L, "OTRO", "Otro", false
                        )
                ),
                List.of(
                        new DatosCatalogosVehiculo.Combustible(
                                1L, "GASOLINA", "Gasolina"
                        ),
                        new DatosCatalogosVehiculo.Combustible(
                                2L, "OTRO", "Otro"
                        )
                ),
                List.of(
                        new DatosCatalogosVehiculo.TipoAceite(
                                1L, "MINERAL", "Mineral"
                        ),
                        new DatosCatalogosVehiculo.TipoAceite(
                                2L, "OTRO", "Otro"
                        )
                )
        );
    }

    private static DatosRegistroVehiculo datosPrueba(
            Long idMotor,
            Long idCombustible,
            Long idAceite,
            String numeroMotor,
            String motorOtro
    ) {
        return new DatosRegistroVehiculo(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "Toyota",
                "Corolla",
                "PARTICULAR",
                "A1B234",
                2020,
                "Blanco",
                idMotor,
                idCombustible,
                1000,
                null,
                null,
                numeroMotor,
                idAceite,
                motorOtro,
                null,
                null
        );
    }
}