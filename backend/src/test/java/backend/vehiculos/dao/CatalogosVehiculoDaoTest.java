package backend.vehiculos.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.flyway.enabled=false"
)
@Transactional(readOnly = true)
class CatalogosVehiculoDaoTest {

    @Autowired
    private CatalogosVehiculoDao catalogosDao;

    @Test
    void recuperaCatalogosDesdePostgreSql() {
        // ARRANGE:
        // PostgreSQL local con las migraciones V1–V4 aplicadas.
        // Spring proporciona el DAO y la conexión real.

        // ACT
        var catalogos = catalogosDao.consultar();

        // ASSERT
        assertFalse(catalogos.usos().isEmpty());
        assertFalse(catalogos.tiposMotor().isEmpty());
        assertFalse(catalogos.combustibles().isEmpty());
        assertFalse(catalogos.tiposAceite().isEmpty());

        assertTrue(
                catalogos.usos().stream().anyMatch(
                        uso -> "PARTICULAR".equals(uso.codigo())
                ),
                "Debe existir el uso PARTICULAR"
        );

        assertTrue(
                catalogos.usos().stream().anyMatch(
                        uso -> "TAXI".equals(uso.codigo())
                ),
                "Debe existir el uso TAXI"
        );

        assertTrue(
                catalogos.tiposMotor().stream().anyMatch(
                        motor -> "ELECTRICO".equals(motor.codigo())
                                && motor.esElectrico()
                                && motor.idTipoMotor() > 0
                ),
                "El motor ELECTRICO debe recuperarse con su indicador"
        );

        assertTrue(
                catalogos.combustibles().stream().allMatch(
                        combustible -> combustible.idCombustible() > 0
                                && !combustible.codigo().isBlank()
                                && !combustible.nombre().isBlank()
                ),
                "Los combustibles deben tener identificador, código y nombre"
        );

        assertTrue(
                catalogos.tiposAceite().stream().allMatch(
                        aceite -> aceite.idTipoAceite() > 0
                                && !aceite.codigo().isBlank()
                                && !aceite.nombre().isBlank()
                ),
                "Los aceites deben tener identificador, código y nombre"
        );
    }
}