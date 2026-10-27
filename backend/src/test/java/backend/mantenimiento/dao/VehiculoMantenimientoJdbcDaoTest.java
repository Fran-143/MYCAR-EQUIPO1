package backend.mantenimiento.dao;

import backend.mantenimiento.service.VehiculoMantenimientoService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(
        named = "mycar.catalogo.tests",
        matches = "true"
)
class VehiculoMantenimientoJdbcDaoTest {

    private static String url;
    private static String usuario;
    private static String password;

    private Connection conexion;
    private JdbcTemplate jdbc;
    private VehiculoMantenimientoJdbcDao dao;
    private Long idUsuario;
    private Long idVehiculo;

    @BeforeAll
    static void prepararBase() {
        url = variableObligatoria("MYCAR_CATALOGO_TEST_URL");
        usuario = variableObligatoria("MYCAR_CATALOGO_TEST_USER");
        password = variableObligatoria("MYCAR_CATALOGO_TEST_PASSWORD");

        if (!url.matches(
                "jdbc:postgresql://127\\.0\\.0\\.1:\\d+/"
                        + "mycar_mvp_test_rf17_[a-f0-9]{8}"
        )) {
            throw new IllegalStateException(
                    "La URL debe apuntar a la BD temporal de RF17"
            );
        }

        Flyway.configure()
                .dataSource(url, usuario, password)
                .schemas("mycar")
                .defaultSchema("mycar")
                .locations("classpath:db/mvp")
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .load()
                .migrate();
    }

    @BeforeEach
    void prepararDatos() throws SQLException {
        conexion = DriverManager.getConnection(url, usuario, password);
        conexion.setAutoCommit(false);

        var dataSource = new SingleConnectionDataSource(
                conexion, true
        );

        jdbc = new JdbcTemplate(dataSource);
        dao = new VehiculoMantenimientoJdbcDao(jdbc);

        idUsuario = jdbc.queryForObject(
                """
                INSERT INTO mycar.usuario (
                    correo,
                    password_hash,
                    nombre,
                    apellido,
                    fecha_nacimiento,
                    consentimiento_privacidad_fecha_utc,
                    consentimiento_privacidad_version
                )
                VALUES (
                    ?, repeat('x', 60), 'Prueba', 'RF17',
                    CURRENT_DATE - INTERVAL '30 years',
                    clock_timestamp(), 'test-v1'
                )
                RETURNING id_usuario
                """,
                Long.class,
                "rf17-" + UUID.randomUUID() + "@example.test"
        );

        idVehiculo = jdbc.queryForObject(
                """
                INSERT INTO mycar.vehiculo (
                    id_usuario,
                    placa,
                    marca,
                    modelo,
                    uso_vehiculo_codigo,
                    anio_fabricacion,
                    color,
                    id_tipo_motor
                )
                VALUES (
                    ?, 'A1B234', 'Marca prueba', 'Modelo prueba',
                    'PARTICULAR', 2020, 'Azul',
                    (
                        SELECT id_tipo_motor
                        FROM mycar.tipo_motor
                        WHERE codigo = 'ELECTRICO'
                    )
                )
                RETURNING id_vehiculo
                """,
                Long.class,
                idUsuario
        );
    }

    @AfterEach
    void revertirDatos() throws SQLException {
        if (conexion != null) {
            try {
                conexion.rollback();
            } finally {
                conexion.close();
            }
        }
    }

    @Test
    void recuperaVehiculoRealYElServicioLoAcepta() {
        var vehiculo = dao.buscarPorId(idVehiculo).orElseThrow();

        assertEquals(idVehiculo, vehiculo.idVehiculo());
        assertEquals(idUsuario, vehiculo.idUsuario());
        assertEquals("ACTIVO", vehiculo.estadoVehiculo());

        var servicio = new VehiculoMantenimientoService(dao);

        assertEquals(
                vehiculo,
                servicio.obtenerVehiculoPropioActivo(
                        idUsuario, idVehiculo
                )
        );
    }

    @Test
    void devuelveVacioCuandoElVehiculoNoExiste() {
        assertTrue(dao.buscarPorId(-1L).isEmpty());
    }

    @Test
    void recuperaVehiculoInactivoYElServicioLoRechaza() {
        int actualizados = jdbc.update(
                """
                UPDATE mycar.vehiculo
                SET estado_vehiculo = 'INACTIVO'
                WHERE id_vehiculo = ?
                """,
                idVehiculo
        );

        assertEquals(1, actualizados);

        var vehiculo = dao.buscarPorId(idVehiculo).orElseThrow();
        assertEquals("INACTIVO", vehiculo.estadoVehiculo());

        var servicio = new VehiculoMantenimientoService(dao);

        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.obtenerVehiculoPropioActivo(
                        idUsuario, idVehiculo
                )
        );
    }

    private static String variableObligatoria(String nombre) {
        String valor = System.getenv(nombre);

        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable " + nombre
                            + ". Ejecuta desde el PowerShell "
                            + "donde preparaste PostgreSQL de pruebas."
            );
        }

        return valor;
    }
}