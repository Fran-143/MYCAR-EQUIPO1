package backend.vehiculos.service;

import backend.vehiculos.dao.CatalogosVehiculoDao;
import backend.vehiculos.dto.DatosCatalogosVehiculo;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidacionRegistroVehiculoServiceTest {

    private static final UUID ID_OPERACION =
            UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock
    private CatalogosVehiculoDao catalogosDao;

    @InjectMocks
    private ValidacionRegistroVehiculoService servicio;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"1HGCM82633A004352"})
    void aceptaVinOpcionalOInformadoConLongitudValida(String vin) {
        // ARRANGE
        when(catalogosDao.consultar()).thenReturn(catalogosPrueba());

        var datos = datosPrueba(
                ID_OPERACION, "PARTICULAR", vin, null
        );

        // ACT
        Set<String> errores = servicio.validar(datos);

        // ASSERT
        assertTrue(errores.isEmpty());
        verify(catalogosDao).consultar();
    }

    @Test
    void identificaDatosComplementariosInvalidos() {
        // ARRANGE
        when(catalogosDao.consultar()).thenReturn(catalogosPrueba());

        var datos = datosPrueba(
                null,
                "DESCONOCIDO",
                "CORTO",
                "S".repeat(81)
        );

        // ACT
        Set<String> errores = servicio.validar(datos);

        // ASSERT
        assertEquals(
                Set.of(
                        "idOperacion",
                        "usoVehiculoCodigo",
                        "numeroVin",
                        "numeroSerie"
                ),
                errores
        );
    }

    @Test
    void propagaFalloDeCatalogosSinPresentarloComoValidacionExitosa() {
        // ARRANGE
        when(catalogosDao.consultar()).thenThrow(
                new DataAccessResourceFailureException(
                        "Base de datos no disponible"
                )
        );

        var datos = datosPrueba(
                ID_OPERACION, "PARTICULAR", null, null
        );

        // ACT / ASSERT
        assertThrows(
                DataAccessResourceFailureException.class,
                () -> servicio.validar(datos)
        );
    }

    private DatosCatalogosVehiculo catalogosPrueba() {
        return new DatosCatalogosVehiculo(
                List.of(
                        new DatosCatalogosVehiculo.UsoVehiculo(
                                "PARTICULAR", "Particular"
                        ),
                        new DatosCatalogosVehiculo.UsoVehiculo(
                                "TAXI", "Taxi"
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

    private DatosRegistroVehiculo datosPrueba(
            UUID idOperacion,
            String uso,
            String vin,
            String numeroSerie
    ) {
        return new DatosRegistroVehiculo(
                idOperacion,
                "Toyota",
                "Corolla",
                uso,
                "A1B234",
                2020,
                "Blanco",
                10L,
                1L,
                50000,
                vin,
                numeroSerie,
                null,
                null,
                null,
                null,
                null
        );
    }
}