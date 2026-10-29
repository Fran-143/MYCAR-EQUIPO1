package backend.mantenimiento.dao;

import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.DatosRegistroMantenimiento.OrigenKmServicio;
import backend.mantenimiento.service.LecturaKmMantenimientoService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(
        named = "mycar.catalogo.tests",
        matches = "true"
)
class LecturaKmMantenimientoJdbcDaoTest {

    private static String url;
    private static String usuario;
    private static String password;

    private Connection conexion;
    private JdbcTemplate jdbc;
    private LecturaKmMantenimientoJdbcDao dao;
    private Long idUsuario;
    private Long idVehiculo;
    private Long idLectura;

    @BeforeAll
    static void prepararBase() throws SQLException {
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

        try (var conexion = DriverManager.getConnection(
                url, usuario, password
        );
             var sentencia = conexion.createStatement();
             var resultado = sentencia.executeQuery(
                     "SHOW server_version_num"
             )) {
            assertTrue(resultado.next());

            int version = Integer.parseInt(resultado.getString(1));
            assertEquals(17, version / 10000);
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
        dao = new LecturaKmMantenimientoJdbcDao(jdbc);

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

        UUID idOperacion = UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO mycar.operacion_idempotente (
                    id_operacion,
                    id_usuario,
                    tipo_operacion,
                    hash_solicitud
                )
                VALUES (?, ?, 'REGISTRAR_VEHICULO', ?)
                """,
                idOperacion,
                idUsuario,
                "a".repeat(64)
        );

        idLectura = jdbc.queryForObject(
                """
                INSERT INTO mycar.lectura_kilometraje (
                    id_vehiculo,
                    valor_km,
                    origen,
                    fecha_lectura_utc,
                    id_operacion
                )
                VALUES (?, 21000, 'INICIAL', ?, ?)
                RETURNING id_lectura_km
                """,
                Long.class,
                idVehiculo,
                OffsetDateTime.parse("2026-10-27T02:00:00Z"),
                idOperacion
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
    void recuperaLecturaConTodosSusDatos() {
        var lectura = dao.buscarPorId(idLectura).orElseThrow();

        assertEquals(idLectura, lectura.idLecturaKm());
        assertEquals(idVehiculo, lectura.idVehiculo());
        assertEquals(Integer.valueOf(21000), lectura.valorKm());
        assertEquals(
                Instant.parse("2026-10-27T02:00:00Z"),
                lectura.fechaLecturaUtc()
        );
    }

    @Test
    void devuelveVacioCuandoLaLecturaNoExiste() {
        assertTrue(dao.buscarPorId(-1L).isEmpty());
    }

    @Test
    void RF17_CP02_EA01_buscaLecturaEfectivaPosteriorDelMismoVehiculoEnLima() {
        // 2026-10-27 02:00 UTC corresponde al 26 de octubre en Lima.
        var posterior = dao.buscarPrimeraLecturaPosterior(
                idVehiculo, LocalDate.of(2026, 10, 25)).orElseThrow();

        assertEquals(idLectura, posterior.idLecturaKm());
        assertEquals(idVehiculo, posterior.idVehiculo());
        assertEquals(Integer.valueOf(21000), posterior.valorKm());
        assertTrue(dao.buscarPrimeraLecturaPosterior(
                idVehiculo, LocalDate.of(2026, 10, 26)).isEmpty());
        assertTrue(dao.buscarPrimeraLecturaPosterior(
                -1L, LocalDate.of(2026, 10, 25)).isEmpty());
    }

    @Test
    void RF17_CP02_EA01_buscaUltimaAnteriorEntreVariasLecturasDelVehiculo() {
        // Tres lecturas del día 26 en Lima; la posterior a 25 es la inicial.
        insertarActualizacion(21500, "2026-10-27T03:00:00Z");
        Long ultima = insertarActualizacion(22000, "2026-10-27T04:00:00Z");

        var anterior = dao.buscarUltimaLecturaAnterior(
                idVehiculo, LocalDate.of(2026, 10, 27)).orElseThrow();

        assertEquals(ultima, anterior.idLecturaKm());
        assertEquals(idVehiculo, anterior.idVehiculo());
        assertEquals(Integer.valueOf(22000), anterior.valorKm());
        assertTrue(dao.buscarUltimaLecturaAnterior(
                idVehiculo, LocalDate.of(2026, 10, 25)).isEmpty());
        assertTrue(dao.buscarUltimaLecturaAnterior(
                -1L, LocalDate.of(2026, 10, 27)).isEmpty());
        assertEquals(idLectura, dao.buscarPrimeraLecturaPosterior(
                idVehiculo, LocalDate.of(2026, 10, 25))
                .orElseThrow().idLecturaKm());
    }

    @Test
    void servicioAceptaLecturaRealSegunFechaDeLima() {
        var servicio = new LecturaKmMantenimientoService(dao);

        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                LocalDate.of(2026, 10, 26),
                21000,
                OrigenKmServicio.LECTURA,
                idLectura,
                null,
                null,
                null,
                null
        );

        assertDoesNotThrow(
                () -> servicio.validarReferencia(idVehiculo, datos)
        );
    }

    private Long insertarActualizacion(int km, String fechaUtc) {
        UUID idOperacion = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO mycar.operacion_idempotente (
                    id_operacion, id_usuario, tipo_operacion, hash_solicitud
                ) VALUES (?, ?, 'ACTUALIZAR_KM', ?)
                """,
                idOperacion, idUsuario, "b".repeat(64)
        );
        return jdbc.queryForObject(
                """
                INSERT INTO mycar.lectura_kilometraje (
                    id_vehiculo, valor_km, origen, fecha_lectura_utc, id_operacion
                ) VALUES (?, ?, 'ACTUALIZACION', ?, ?)
                RETURNING id_lectura_km
                """,
                Long.class, idVehiculo, km,
                OffsetDateTime.parse(fechaUtc), idOperacion
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
