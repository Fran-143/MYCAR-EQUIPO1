package backend.vehiculos.service;

import backend.vehiculos.dao.CatalogosVehiculoDao;
import backend.vehiculos.dto.DatosCatalogosVehiculo;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidacionIdentificacionRegistroServiceTest {

    @Mock
    private CatalogosVehiculoDao catalogosDao;

    @InjectMocks
    private ValidacionRegistroVehiculoService servicio;

    @ParameterizedTest(name = "{0}")
    @MethodSource("casosIdentificacion")
    void aplicaLasReglasDeIdentificacionDesdeElServicio(
            String escenario,
            String vin,
            String numeroMotor,
            String numeroSerie,
            Set<String> erroresEsperados
    ) {
        // ARRANGE
        when(catalogosDao.consultar()).thenReturn(catalogosPrueba());

        var datos = new DatosRegistroVehiculo(
                UUID.randomUUID(),
                "Toyota",
                "Corolla",
                "PARTICULAR",
                "A1B234",
                2020,
                "Blanco",
                10L,
                1L,
                50000,
                vin,
                numeroSerie,
                numeroMotor,
                null,
                null,
                null,
                null
        );

        // ACT
        Set<String> errores = servicio.validar(datos);

        // ASSERT
        assertEquals(erroresEsperados, errores);
    }

    private static Stream<Arguments> casosIdentificacion() {
        return Stream.of(
                Arguments.of(
                        "Identificadores válidos",
                        "1HGCM82633A004352",
                        "MOTOR123",
                        "SERIE123",
                        Set.of()
                ),
                Arguments.of(
                        "Identificadores opcionales omitidos",
                        null,
                        null,
                        null,
                        Set.of()
                ),
                Arguments.of(
                        "VIN con letra I prohibida",
                        "IHGCM82633A004352",
                        null,
                        null,
                        Set.of("numeroVin")
                ),
                Arguments.of(
                        "Número de motor demasiado corto",
                        null,
                        "AB12",
                        null,
                        Set.of("numeroMotor")
                ),
                Arguments.of(
                        "Número de serie con guion",
                        null,
                        null,
                        "ABC-123",
                        Set.of("numeroSerie")
                ),
                Arguments.of(
                        "Tres identificadores inválidos",
                        "1hgcm82633a004352",
                        "motor123",
                        "ABC12",
                        Set.of("numeroVin", "numeroMotor", "numeroSerie")
                )
        );
    }

    private DatosCatalogosVehiculo catalogosPrueba() {
        return new DatosCatalogosVehiculo(
                List.of(
                        new DatosCatalogosVehiculo.UsoVehiculo(
                                "PARTICULAR", "Particular"
                        )
                ),
                List.of(
                        new DatosCatalogosVehiculo.TipoMotor(
                                10L, "COMBUSTION", "Combustión", false
                        )
                ),
                List.of(
                        new DatosCatalogosVehiculo.Combustible(
                                1L, "GASOLINA", "Gasolina"
                        )
                ),
                List.of()
        );
    }
}