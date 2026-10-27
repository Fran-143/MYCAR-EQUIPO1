package backend.mantenimiento.dao;

import backend.mantenimiento.service.TipoMantenimientoService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(
        named = "mycar.catalogo.tests",
        matches = "true"
)
class TipoMantenimientoJdbcDaoTest {

    private static String url;
    private static String usuario;
    private static String password;

    private Connection conexion;
    private JdbcTemplate jdbc;
    private TipoMantenimientoJdbcDao dao;

    @BeforeAll
    static void prepararBaseAislada() throws Exception {
        url = variableObligatoria("MYCAR_CATALOGO_TEST_URL");
        usuario = variableObligatoria("MYCAR_CATALOGO_TEST_USER");
        password = variableObligatoria("MYCAR_CATALOGO_TEST_PASSWORD");

        // Protección: únicamente nuestra base temporal de loopback.
        if (!url.matches(
                "jdbc:postgresql://127\\.0\\.0\\.1:\\d+/"
                        + "mycar_mvp_test_rf17_[a-f0-9]{8}")) {
            throw new IllegalStateException(
                    "Se requiere la base aislada preparada para RF17"
            );
        }

        try (Connection conexion = DriverManager.getConnection(
                url, usuario, password);
             var consulta = conexion.createStatement();
             var resultado = consulta.executeQuery(
                     "SHOW server_version_num")) {

            resultado.next();
            int version = resultado.getInt(1);
            assertTrue(
                    version >= 170000 && version < 180000,
                    "La prueba requiere PostgreSQL 17"
            );
        }

        // Aplica las migraciones existentes solo en la base temporal.
        Flyway.configure()
                .dataSource(url, usuario, password)
                .defaultSchema("mycar")
                .locations("classpath:db/mvp")
                .load()
                .migrate();
    }

    @BeforeEach
    void prepararTransaccion() throws Exception {
        conexion = DriverManager.getConnection(url, usuario, password);
        conexion.setAutoCommit(false);

        var fuente = new SingleConnectionDataSource(conexion, true);
        jdbc = new JdbcTemplate(fuente);
        dao = new TipoMantenimientoJdbcDao(jdbc);
    }

    @AfterEach
    void revertirCambios() throws Exception {
        if (conexion != null) {
            try {
                conexion.rollback();
            } finally {
                conexion.close();
            }
        }
    }

    @Test
    void recuperaTipoExistenteConSusDatosReales() {
        // ARRANGE: V3 ya cargó CAMBIO_ACEITE.

        // ACT
        var tipo = dao.buscarPorCodigo("CAMBIO_ACEITE")
                .orElseThrow();

        // ASSERT
        assertTrue(tipo.idTipoMantenimiento() > 0);
        assertEquals("CAMBIO_ACEITE", tipo.codigo());
        assertEquals("Cambio de aceite", tipo.nombre());
        assertTrue(tipo.activo());
    }

    @Test
    void devuelveVacioCuandoElCodigoNoExiste() {
        // ACT
        var resultado = dao.buscarPorCodigo(
                "TIPO_INEXISTENTE_PRUEBA_RF17"
        );

        // ASSERT
        assertTrue(resultado.isEmpty());
    }

    @Test
    void recuperaTipoInactivoYElServicioLoRechaza() {
        // ARRANGE: cambio temporal, revertido al finalizar la prueba.
        int modificados = jdbc.update(
                """
                UPDATE mycar.tipo_mantenimiento
                SET activo = FALSE
                WHERE codigo = ?
                """,
                "CAMBIO_ACEITE"
        );

        assertEquals(1, modificados);
        var servicio = new TipoMantenimientoService(dao);

        // ACT
        var tipo = dao.buscarPorCodigo("CAMBIO_ACEITE")
                .orElseThrow();

        // ASSERT
        assertFalse(tipo.activo());
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.obtenerTipoActivo("CAMBIO_ACEITE")
        );
    }

    private static String variableObligatoria(String nombre) {
        String valor = System.getenv(nombre);

        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar la variable " + nombre
            );
        }

        return valor;
    }
}