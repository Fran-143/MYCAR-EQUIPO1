package backend.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import backend.vehiculos.dao.VehiculoDao;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import backend.vehiculos.dao.OperacionVehiculoDao;
import backend.vehiculos.dao.OperacionVehiculoDao;

/** PostgreSQL real, opt-in y exclusivamente en bases desechables mycar_mvp_test_*. */
@EnabledIfSystemProperty(named = "mycar.db.tests", matches = "true")
class MvpDatabaseTest {
    static final ZoneId LIMA = ZoneId.of("America/Lima");
    static final Set<String> TABLES = Set.of("usuario", "plan", "suscripcion", "preferencia_notificacion",
            "config_recordatorio_km_usuario", "sesion", "intento_login_fallido", "uso_vehiculo", "tipo_motor",
            "combustible", "tipo_aceite", "tipo_mantenimiento", "vehiculo", "operacion_idempotente",
            "lectura_kilometraje", "config_recordatorio_km", "mantenimiento", "repuesto",
            "mantenimiento_repuesto", "alerta", "historial_alerta");
    static String url, user, password;
    Connection c;

    @BeforeAll static void migrate() throws Exception {
        url = required("MYCAR_TEST_JDBC_URL");
        user = required("MYCAR_TEST_USER");
        password = required("MYCAR_TEST_PASSWORD");
        assertTrue(url.matches("jdbc:postgresql://[^/]+/mycar_mvp_test_[a-z0-9_]+"),
                "Solo se permiten bases desechables mycar_mvp_test_*; no utilizar mycar_mvp real");
        try (Connection db = open()) {
            assertTrue(text(db, "SHOW server_version").startsWith("17."), "Se requiere PostgreSQL 17");
            assertEquals(0, number(db, "SELECT count(*) FROM pg_tables WHERE schemaname='mycar'"),
                    "La suite exige una BD nueva; no limpia datos existentes");
        }
        flyway(url, "1").migrate();
        try (Connection db = open()) {
            exec(db, "CREATE TABLE public.evidencia_preservada(valor TEXT PRIMARY KEY)");
            exec(db, "INSERT INTO public.evidencia_preservada VALUES ('no modificar')");
        }
        assertEquals(4, flyway(url, null).migrate().migrationsExecuted);
        flyway(url, null).validate();
    }

    @BeforeEach void begin() throws Exception { c = open(); c.setAutoCommit(false); }
    @AfterEach void rollback() throws Exception { if (c != null) { c.rollback(); c.close(); } }
    @AfterAll static void noFixturesPersisted() throws Exception {
        try (Connection db = open()) {
            for (String table : TABLES) {
                if (!Set.of("plan", "uso_vehiculo", "tipo_motor", "combustible", "tipo_aceite", "tipo_mantenimiento").contains(table))
                    assertEquals(0, number(db, "SELECT count(*) FROM mycar." + table), table);
            }
        }
    }

    @Test
    void vehiculoDaoBuscaGlobalmenteYCuentaActivosEInactivosPorUsuario()
            throws Exception {

        // ARRANGE: reutilizamos la conexión de prueba y su rollback.
        var fuente = new SingleConnectionDataSource(c, true);
        var dao = new VehiculoDao(new JdbcTemplate(fuente));

        long propietario = account(c);

        long otroPropietario = accountWith(
                c,
                "otro-dao@example.test",
                LocalDate.now(LIMA).minusYears(30)
        );

        assertEquals(0L, dao.contarRegistrados(propietario));

        rawVehicle(
                c, propietario, "A1B234",
                "COMBUSTION", "GASOLINA", null
        );

        long inactivo = rawVehicle(
                c, propietario, "C3D456",
                "COMBUSTION", "GASOLINA", null
        );

        long ajeno = rawVehicle(
                c, otroPropietario, "E5F678",
                "COMBUSTION", "GASOLINA", null
        );

        String vinInactivo = "1HGCM82633A004352";
        String vinAjeno = "1HGCM82633A004353";

        exec(
                c,
                """
                UPDATE mycar.vehiculo
                SET estado_vehiculo = 'INACTIVO', numero_vin = ?
                WHERE id_vehiculo = ?
                """,
                vinInactivo,
                inactivo
        );

        exec(
                c,
                """
                UPDATE mycar.vehiculo
                SET numero_vin = ?
                WHERE id_vehiculo = ?
                """,
                vinAjeno,
                ajeno
        );

        // ACT / ASSERT: placa global, incluyendo inactivos y otras cuentas.
        assertTrue(dao.existePlaca("A1B234"));
        assertTrue(dao.existePlaca("C3D456"));
        assertTrue(dao.existePlaca("E5F678"));
        assertFalse(dao.existePlaca("Z9Z999"));

        // VIN global y opcional.
        assertTrue(dao.existeVin(vinInactivo));
        assertTrue(dao.existeVin(vinAjeno));
        assertFalse(dao.existeVin("1HGCM82633A004354"));
        assertFalse(dao.existeVin(null));

        // El conteo incluye inactivos y distingue propietarios.
        assertEquals(2L, dao.contarRegistrados(propietario));
        assertEquals(1L, dao.contarRegistrados(otroPropietario));
    }

    @Test
    void vehiculoDaoInsertaVehiculoConPropietarioYDatosCorrectos()
            throws Exception {

        // ARRANGE
        var fuente = new SingleConnectionDataSource(c, true);
        var dao = new VehiculoDao(new JdbcTemplate(fuente));

        long propietario = account(c);

        long idMotor = number(
                c,
                """
                SELECT id_tipo_motor
                FROM mycar.tipo_motor
                WHERE codigo = 'COMBUSTION'
                """
        );

        long idCombustible = number(
                c,
                """
                SELECT id_combustible
                FROM mycar.combustible
                WHERE codigo = 'GASOLINA'
                """
        );

        var datos = new DatosRegistroVehiculo(
                UUID.randomUUID(),
                "Toyota",
                "Corolla",
                "PARTICULAR",
                "A1B234",
                2020,
                "Blanco",
                idMotor,
                idCombustible,
                50000,
                "1HGCM82633A004352",
                "SERIE123",
                "MOTOR123",
                null,
                null,
                null,
                null
        );

        // ACT
        long idVehiculo =
                dao.insertarVehiculo(propietario, datos, true);

        // ASSERT
        assertTrue(idVehiculo > 0);

        assertEquals(
                propietario,
                number(
                        c,
                        "SELECT id_usuario FROM mycar.vehiculo WHERE id_vehiculo=?",
                        idVehiculo
                )
        );

        assertEquals(
                "A1B234",
                text(
                        c,
                        "SELECT placa FROM mycar.vehiculo WHERE id_vehiculo=?",
                        idVehiculo
                )
        );

        assertEquals(
                "Toyota|Corolla|Blanco",
                text(
                        c,
                        """
                        SELECT marca || '|' || modelo || '|' || color
                        FROM mycar.vehiculo
                        WHERE id_vehiculo=?
                        """,
                        idVehiculo
                )
        );

        assertEquals(
                "1HGCM82633A004352",
                text(
                        c,
                        "SELECT numero_vin FROM mycar.vehiculo WHERE id_vehiculo=?",
                        idVehiculo
                )
        );

        assertEquals(
                "SERIE123",
                text(
                        c,
                        "SELECT numero_serie FROM mycar.vehiculo WHERE id_vehiculo=?",
                        idVehiculo
                )
        );

        assertEquals(
                "MOTOR123",
                text(
                        c,
                        "SELECT numero_motor FROM mycar.vehiculo WHERE id_vehiculo=?",
                        idVehiculo
                )
        );

        assertEquals(
                "ACTIVO",
                text(
                        c,
                        "SELECT estado_vehiculo FROM mycar.vehiculo WHERE id_vehiculo=?",
                        idVehiculo
                )
        );

        assertEquals(
                1L,
                number(
                        c,
                        """
                        SELECT count(*)
                        FROM mycar.vehiculo
                        WHERE id_vehiculo=? AND es_principal
                        """,
                        idVehiculo
                )
        );
    }

    @Test
    void bdValidaFormatoVinYPermiteOmitirlo() throws Exception {
        // ARRANGE
        long propietario = account(c);
        long vehiculo = rawVehicle(
                c, propietario, "A1B234",
                "COMBUSTION", "GASOLINA", null
        );

        String actualizar =
                "UPDATE mycar.vehiculo SET numero_vin=? WHERE id_vehiculo=?";

        // ACT / ASSERT: valores permitidos.
        for (String vin : List.of(
                "1HGCM82633A004352",
                "12345678901234567"
        )) {
            exec(c, actualizar, vin, vehiculo);

            assertEquals(
                    vin,
                    text(
                            c,
                            "SELECT numero_vin FROM mycar.vehiculo WHERE id_vehiculo=?",
                            vehiculo
                    )
            );
        }

        // Formatos que deben producir una violación CHECK.
        for (String vin : List.of(
                "IHGCM82633A004352",
                "OHGCM82633A004352",
                "QHGCM82633A004352",
                "1hgcm82633a004352",
                "1HGCM82633A00435-"
        )) {
            invalid("23514", () -> exec(c, actualizar, vin, vehiculo));
        }

        exec(c, actualizar, null, vehiculo);

        assertNull(text(
                c,
                "SELECT numero_vin FROM mycar.vehiculo WHERE id_vehiculo=?",
                vehiculo
        ));
    }

    @Test
    void bdValidaNumeroMotorYPermiteOmitirlo() throws Exception {
        // ARRANGE
        long propietario = account(c);
        long vehiculo = rawVehicle(
                c, propietario, "A1B234",
                "COMBUSTION", "GASOLINA", null
        );

        String actualizar =
                "UPDATE mycar.vehiculo SET numero_motor=? WHERE id_vehiculo=?";

        // ACT / ASSERT: límites y caracteres permitidos.
        for (String motor : List.of(
                "AB-12",
                "AB 12",
                "MOTOR123",
                "ABCDEFGHIJKLMNO"
        )) {
            exec(c, actualizar, motor, vehiculo);

            assertEquals(
                    motor,
                    text(
                            c,
                            "SELECT numero_motor FROM mycar.vehiculo WHERE id_vehiculo=?",
                            vehiculo
                    )
            );
        }

        for (String motor : List.of(
                "AB12",
                "ABCDEFGHIJKLMNOP",
                "motor123",
                "AB_12",
                "     ",
                "AB\t12",
                "AB\n12"
        )) {
            invalid("23514", () -> exec(c, actualizar, motor, vehiculo));
        }

        exec(c, actualizar, null, vehiculo);

        assertNull(text(
                c,
                "SELECT numero_motor FROM mycar.vehiculo WHERE id_vehiculo=?",
                vehiculo
        ));
    }

    @Test
    void bdValidaNumeroSerieYPermiteOmitirlo() throws Exception {
        // ARRANGE
        long propietario = account(c);
        long vehiculo = rawVehicle(
                c, propietario, "A1B234",
                "COMBUSTION", "GASOLINA", null
        );

        String actualizar =
                "UPDATE mycar.vehiculo SET numero_serie=? WHERE id_vehiculo=?";

        // ACT / ASSERT: mínimo y máximo permitidos.
        for (String serie : List.of("ABC123", "A".repeat(80))) {
            exec(c, actualizar, serie, vehiculo);

            assertEquals(
                    serie,
                    text(
                            c,
                            "SELECT numero_serie FROM mycar.vehiculo WHERE id_vehiculo=?",
                            vehiculo
                    )
            );
        }

        for (String serie : List.of(
                "ABC12",
                "",
                "abc123",
                "ABC 123",
                "ABC-123",
                "ABC_123"
        )) {
            invalid("23514", () -> exec(c, actualizar, serie, vehiculo));
        }

        invalid(
                "22001",
                () -> exec(c, actualizar, "A".repeat(81), vehiculo)
        );

        exec(c, actualizar, null, vehiculo);

        assertNull(text(
                c,
                "SELECT numero_serie FROM mycar.vehiculo WHERE id_vehiculo=?",
                vehiculo
        ));
    }

    @Test
    void reservaOperacionSinDuplicarlaNiCambiarSuContenido()
            throws Exception {

        // ARRANGE
        var fuente = new SingleConnectionDataSource(c, true);
        var dao = new OperacionVehiculoDao(new JdbcTemplate(fuente));

        long propietario = account(c);

        long otroPropietario = accountWith(
                c,
                "otra-operacion@example.test",
                LocalDate.now(LIMA).minusYears(30)
        );

        UUID idOperacion = UUID.randomUUID();

        // Huellas ficticias utilizadas exclusivamente en esta prueba.
        String hashOriginal = "a".repeat(64);
        String hashDistinto = "b".repeat(64);

        // ACT / ASSERT: la primera solicitud reserva la operación.
        assertTrue(dao.reservar(
                idOperacion,
                propietario,
                "REGISTRAR_VEHICULO",
                hashOriginal
        ));

        // Repetir la clave no inserta otra fila.
        assertFalse(dao.reservar(
                idOperacion,
                propietario,
                "REGISTRAR_VEHICULO",
                hashOriginal
        ));

        // Tampoco reemplaza el contenido original.
        assertFalse(dao.reservar(
                idOperacion,
                propietario,
                "REGISTRAR_VEHICULO",
                hashDistinto
        ));

        // La clave es global: otra cuenta tampoco puede reemplazarla.
        assertFalse(dao.reservar(
                idOperacion,
                otroPropietario,
                "REGISTRAR_VEHICULO",
                hashOriginal
        ));

        assertEquals(
                1L,
                number(
                        c,
                        """
                        SELECT count(*)
                        FROM mycar.operacion_idempotente
                        WHERE id_operacion=?
                        """,
                        idOperacion
                )
        );

        assertEquals(
                propietario,
                number(
                        c,
                        """
                        SELECT id_usuario
                        FROM mycar.operacion_idempotente
                        WHERE id_operacion=?
                        """,
                        idOperacion
                )
        );

        assertEquals(
                hashOriginal,
                text(
                        c,
                        """
                        SELECT hash_solicitud
                        FROM mycar.operacion_idempotente
                        WHERE id_operacion=?
                        """,
                        idOperacion
                )
        );

        assertEquals(
                1L,
                number(
                        c,
                        """
                        SELECT count(*)
                        FROM mycar.operacion_idempotente
                        WHERE id_operacion=?
                          AND tipo_operacion='REGISTRAR_VEHICULO'
                          AND estado='EN_PROCESO'
                          AND respuesta_json IS NULL
                        """,
                        idOperacion
                )
        );
    }

    @Test
    void consultaOperacionSoloParaSuPropietarioYTipo()
            throws Exception {

        var fuente = new SingleConnectionDataSource(c, true);
        var dao = new OperacionVehiculoDao(new JdbcTemplate(fuente));

        long propietario = account(c);

        long otroPropietario = accountWith(
                c,
                "consulta-operacion@example.test",
                LocalDate.now(LIMA).minusYears(30)
        );

        UUID idOperacion = UUID.randomUUID();
        String hashSolicitud = "a".repeat(64);

        assertTrue(dao.reservar(
                idOperacion,
                propietario,
                "REGISTRAR_VEHICULO",
                hashSolicitud
        ));

        var operacion = dao.buscarParaActualizar(
                idOperacion,
                propietario,
                "REGISTRAR_VEHICULO"
        ).orElseThrow();

        assertEquals(hashSolicitud, operacion.hashSolicitud());
        assertEquals("EN_PROCESO", operacion.estado());
        assertNull(operacion.respuestaJson());

        // Otra cuenta no puede consultar esta operación.
        assertTrue(dao.buscarParaActualizar(
                idOperacion,
                otroPropietario,
                "REGISTRAR_VEHICULO"
        ).isEmpty());

        // Tampoco se recupera como un tipo de operación distinto.
        assertTrue(dao.buscarParaActualizar(
                idOperacion,
                propietario,
                "ACTUALIZAR_KM"
        ).isEmpty());

        // Un identificador inexistente no devuelve resultados.
        assertTrue(dao.buscarParaActualizar(
                UUID.randomUUID(),
                propietario,
                "REGISTRAR_VEHICULO"
        ).isEmpty());
    }

    @Test
    void confirmaOperacionSoloSiCoincidenPropietarioTipoYHash()
            throws Exception {

        var fuente = new SingleConnectionDataSource(c, true);
        var dao = new OperacionVehiculoDao(new JdbcTemplate(fuente));

        long propietario = account(c);

        long otroPropietario = accountWith(
                c,
                "confirmacion-operacion@example.test",
                LocalDate.now(LIMA).minusYears(30)
        );

        UUID idOperacion = UUID.randomUUID();
        String hashOriginal = "a".repeat(64);
        String hashDistinto = "b".repeat(64);

        // Respuesta ficticia exclusiva de esta prueba.
        String respuesta = "{\"prueba\":\"confirmacion\"}";

        assertTrue(dao.reservar(
                idOperacion,
                propietario,
                "REGISTRAR_VEHICULO",
                hashOriginal
        ));

        assertFalse(dao.confirmar(
                idOperacion,
                otroPropietario,
                "REGISTRAR_VEHICULO",
                hashOriginal,
                respuesta
        ));

        assertFalse(dao.confirmar(
                idOperacion,
                propietario,
                "ACTUALIZAR_KM",
                hashOriginal,
                respuesta
        ));

        assertFalse(dao.confirmar(
                idOperacion,
                propietario,
                "REGISTRAR_VEHICULO",
                hashDistinto,
                respuesta
        ));

        var pendiente = dao.buscarParaActualizar(
                idOperacion,
                propietario,
                "REGISTRAR_VEHICULO"
        ).orElseThrow();

        assertEquals("EN_PROCESO", pendiente.estado());
        assertNull(pendiente.respuestaJson());

        assertTrue(dao.confirmar(
                idOperacion,
                propietario,
                "REGISTRAR_VEHICULO",
                hashOriginal,
                respuesta
        ));

        // Una operación confirmada no puede reemplazar su respuesta.
        assertFalse(dao.confirmar(
                idOperacion,
                propietario,
                "REGISTRAR_VEHICULO",
                hashOriginal,
                "{\"prueba\":\"reemplazo\"}"
        ));

        assertEquals(
                1L,
                number(
                        c,
                        """
                        SELECT count(*)
                        FROM mycar.operacion_idempotente
                        WHERE id_operacion = ?
                          AND estado = 'CONFIRMADA'
                          AND respuesta_json = CAST(? AS jsonb)
                        """,
                        idOperacion,
                        respuesta
                )
        );
    }

    @Test void flywayV1IntactaActualizaYReintentaSinModificarDatos() throws Exception {
        try (var in = getClass().getResourceAsStream("/db/mvp/V1__esquema_mvp.sql")) {
            assertNotNull(in);
            assertEquals("65e5a7bef83ce744310d3540fb4055b382819c8e3ae70ce8ec6b5535142e319b",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                            new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8))));
        }
        assertEquals(5, number(c, "SELECT count(*) FROM mycar.flyway_schema_history WHERE version IS NOT NULL AND success"));
        assertEquals(0, flyway(url, null).migrate().migrationsExecuted);
        assertEquals("no modificar", text(c, "SELECT valor FROM public.evidencia_preservada"));
        assertEquals(7, number(c, "SELECT count(*) FROM mycar.tipo_mantenimiento"));
    }

    @Test void estructuraTiene21TablasClavesYIndicesParciales() throws Exception {
        Set<String> actual = new HashSet<>();
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='mycar' AND tablename<>'flyway_schema_history'")) {
            while (r.next()) actual.add(r.getString(1));
        }
        assertEquals(TABLES, actual);
        assertEquals(21, number(c, "SELECT count(*) FROM pg_constraint con JOIN pg_namespace n ON n.oid=con.connamespace JOIN pg_class t ON t.oid=con.conrelid WHERE n.nspname='mycar' AND con.contype='p' AND t.relname<>'flyway_schema_history'"));
        for (String index : List.of("uq_suscripcion_activa_usuario", "uq_sesion_vigente_instalacion",
                "uq_vehiculo_principal_usuario", "uq_lectura_inicial_por_vehiculo", "uq_alerta_equivalente_activa")) {
            assertTrue(text(c, "SELECT indexdef FROM pg_indexes WHERE schemaname='mycar' AND indexname=?", index).contains("WHERE"));
        }
        assertEquals(3, number(c, "SELECT count(*) FROM pg_constraint WHERE conname IN ('fk_mantenimiento_lectura_mismo_vehiculo','fk_alerta_mantenimiento_mismo_vehiculo','fk_alerta_lectura_mismo_vehiculo')"));
        assertTrue(number(c, "SELECT count(*) FROM pg_constraint WHERE connamespace='mycar'::regnamespace AND contype='c'") >= 45);
    }

    @Test void columnasDelDiccionarioAprobadoEstanPresentes() throws Exception {
        try (var in = getClass().getResourceAsStream("/db/mvp/dictionary_columns.json")) {
            assertNotNull(in);
            var inventory = new com.fasterxml.jackson.databind.ObjectMapper().readTree(in);
            assertEquals(21, inventory.size());
            var entries = inventory.fields();
            int checked = 0;
            while (entries.hasNext()) {
                var entry = entries.next();
                for (var column : entry.getValue()) {
                    assertEquals(1, number(c, "SELECT count(*) FROM information_schema.columns WHERE table_schema='mycar' AND table_name=? AND column_name=?", entry.getKey(), column.asText()), entry.getKey() + "." + column.asText());
                    checked++;
                }
            }
            assertEquals(149, checked);
        }
    }

    @Test void catalogosSonLosAprobadosSinPremiumOperativo() throws Exception {
        assertEquals("FREE:1", text(c, "SELECT codigo||':'||max_vehiculos_registrados FROM mycar.plan WHERE activo"));
        assertEquals(1, number(c, "SELECT count(*) FROM mycar.plan"));
        assertEquals(2, number(c, "SELECT count(*) FROM mycar.uso_vehiculo WHERE codigo IN ('PARTICULAR','TAXI')"));
        assertEquals(7, number(c, "SELECT count(*) FROM mycar.tipo_mantenimiento WHERE activo"));
        assertEquals(1, number(c, "SELECT count(*) FROM mycar.tipo_motor WHERE es_electrico AND codigo='ELECTRICO'"));
        assertEquals(1, number(c, "SELECT count(*) FROM mycar.tipo_motor WHERE es_electrico"));
    }

    @Test void cuentaCorreoEdadConsentimientoYRol() throws Exception {
        account(c);
        invalid("23505", () -> accountWith(c, "EVIDENCIA@example.test", LocalDate.now(LIMA).minusYears(30)));
        invalid(null, () -> accountWith(c, "menor@example.test", LocalDate.now(LIMA).minusYears(18).plusDays(1)));
        invalid(null, () -> accountWith(c, "mayor@example.test", LocalDate.now(LIMA).minusYears(86)));
        accountWith(c, "minimo@example.test", LocalDate.now(LIMA).minusYears(18));
        accountWith(c, "maximo@example.test", LocalDate.now(LIMA).minusYears(85));
        invalid("23514", () -> exec(c, "UPDATE mycar.usuario SET codigo_rol='ADMINISTRADOR'"));
        invalid("23502", () -> exec(c, "UPDATE mycar.usuario SET consentimiento_privacidad_version=NULL"));
        invalid("23514", () -> exec(c, "UPDATE mycar.usuario SET consentimiento_privacidad_version=' '"));
    }

    @Test void altaCuentaEsAtomicaYFreeNoSeDuplica() throws Exception {
        Savepoint start = c.setSavepoint();
        long u = account(c);
        configureAccount(c, u);
        invalid("23505", () -> exec(c, "INSERT INTO mycar.suscripcion(id_usuario,id_plan,estado) SELECT ?,id_plan,'ACTIVA' FROM mycar.plan WHERE codigo='FREE'", u));
        assertEquals("PAUSADO", text(c, "SELECT estado_recordatorios FROM mycar.config_recordatorio_km_usuario WHERE id_usuario=?", u));
        assertEquals(1, number(c, "SELECT count(*) FROM mycar.preferencia_notificacion WHERE id_usuario=? AND mantenimiento_on AND soat_on AND kilometraje_on AND permiso_dispositivo='NO_SOLICITADO'", u));
        c.rollback(start);
        assertEquals(0, number(c, "SELECT count(*) FROM mycar.usuario"));
        assertEquals(0, number(c, "SELECT count(*) FROM mycar.suscripcion"));
    }

    @Test void sesionesPorInstalacionYLogoutIndividual() throws Exception {
        long u = account(c); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        long s1 = session(c, u, a, "a".repeat(64));
        long s2 = session(c, u, b, "b".repeat(64));
        invalid("23505", () -> session(c, u, a, "c".repeat(64)));
        exec(c, "UPDATE mycar.sesion SET estado_sesion='REVOCADA',revocada_utc=clock_timestamp() WHERE id_sesion=?", s1);
        session(c, u, a, "c".repeat(64));
        assertEquals("VIGENTE", text(c, "SELECT estado_sesion FROM mycar.sesion WHERE id_sesion=?", s2));
        exec(c, "UPDATE mycar.sesion SET estado_sesion='EXPIRADA' WHERE id_sesion=?", s2);
        session(c, u, b, "d".repeat(64));
        invalid("23514", () -> exec(c, "UPDATE mycar.sesion SET estado_sesion='REVOCADA',revocada_utc=NULL WHERE estado_sesion='VIGENTE'"));
    }

    @Test void fallosLoginVentanaMovilRealYBloqueoSinExtender() throws Exception {
        long u = account(c);
        session(c, u, UUID.randomUUID(), "a".repeat(64));
        exec(c, "INSERT INTO mycar.intento_login_fallido(id_usuario,ocurrido_utc) VALUES (?,clock_timestamp()-interval '9 minutes'),(?,clock_timestamp()-interval '4 minutes')", u, u);
        String until = text(c, "SELECT mycar.registrar_fallo_login(?)", u);
        assertNotNull(until);
        assertEquals(until, text(c, "SELECT mycar.registrar_fallo_login(?)", u));
        assertEquals(3, number(c, "SELECT count(*) FROM mycar.intento_login_fallido"));
        assertEquals("ACTIVA", text(c, "SELECT estado_cuenta FROM mycar.usuario WHERE id_usuario=?", u));
        assertEquals("VIGENTE", text(c, "SELECT estado_sesion FROM mycar.sesion WHERE id_usuario=?", u));
    }

    @Test void fallosSeparadosNoBloqueanYLoginExitosoReiniciaSinBorrar() throws Exception {
        long u = account(c);
        exec(c, "INSERT INTO mycar.intento_login_fallido(id_usuario,ocurrido_utc) VALUES (?,clock_timestamp()-interval '12 minutes'),(?,clock_timestamp()-interval '1 minute')", u, u);
        assertNull(text(c, "SELECT mycar.registrar_fallo_login(?)", u));
        exec(c, "SELECT mycar.reiniciar_fallos_login(?)", u);
        assertNull(text(c, "SELECT mycar.registrar_fallo_login(?)", u));
        assertEquals(4, number(c, "SELECT count(*) FROM mycar.intento_login_fallido"));
        exec(c, "UPDATE mycar.usuario SET login_bloqueo_hasta_utc=clock_timestamp()-interval '1 microsecond' WHERE id_usuario=?", u);
        assertNull(text(c, "SELECT mycar.registrar_fallo_login(?)", u));
    }

    @Test void placaGlobalVinOpcionalYFormatosInvalidos() throws Exception {
        Vehicle a = fixture(c);
        long other = accountWith(c, "otra@example.test", LocalDate.now(LIMA).minusYears(30));
        invalid("23505", () -> rawVehicle(c, other, "A1B234", "COMBUSTION", "GASOLINA", null));
        for (String plate : List.of("ABC123", "a1B234", "A1B23", "A1B-23", "A1B 23"))
            invalid("23514", () -> rawVehicle(c, other, plate, "COMBUSTION", "GASOLINA", null));
        exec(c, "UPDATE mycar.vehiculo SET numero_vin='12345678901234567' WHERE id_vehiculo=?", a.id);
        long v2 = rawVehicle(c, other, "B2C345", "COMBUSTION", "GASOLINA", null);
        invalid("23505", () -> exec(c, "UPDATE mycar.vehiculo SET numero_vin='12345678901234567' WHERE id_vehiculo=?", v2));
        assertEquals(1, number(c, "SELECT count(*) FROM mycar.vehiculo WHERE numero_vin IS NULL"));
    }

    @Test void motoresOtroYAnioRespetanReglas() throws Exception {
        long u = account(c);
        long electric = rawVehicle(c, u, "A1B234", "ELECTRICO", null, null);
        invalid(null, () -> exec(c, "UPDATE mycar.vehiculo SET id_combustible=(SELECT id_combustible FROM mycar.combustible WHERE codigo='GASOLINA') WHERE id_vehiculo=?", electric));
        invalid(null, () -> rawVehicle(c, u, "B2C345", "OTRO", "GASOLINA", null));
        long other = rawVehicle(c, u, "B2C345", "OTRO", "GASOLINA", "Motor personalizado");
        invalid(null, () -> exec(c, "UPDATE mycar.vehiculo SET id_combustible=(SELECT id_combustible FROM mycar.combustible WHERE codigo='OTRO') WHERE id_vehiculo=?", other));
        invalid(null, () -> exec(c, "UPDATE mycar.vehiculo SET anio_fabricacion=? WHERE id_vehiculo=?", LocalDate.now(LIMA).getYear() + 1, other));
        exec(c, "UPDATE mycar.vehiculo SET anio_fabricacion=1800 WHERE id_vehiculo=?", other);
        assertEquals(1800, number(c, "SELECT anio_fabricacion FROM mycar.vehiculo WHERE id_vehiculo=?", other));
    }

    @Test void rf08ContratoSqlIdempotenciaAntesDeCupoYRollback() throws Exception {
        long u = account(c); configureAccount(c, u);
        UUID op = UUID.randomUUID();
        long id = registerVehicle(c, u, op, "a".repeat(64), "A1B234");
        assertEquals(id, registerVehicle(c, u, op, "a".repeat(64), "A1B234"));
        invalid(null, () -> registerVehicle(c, u, op, "b".repeat(64), "A1B234"));
        exec(c, "UPDATE mycar.vehiculo SET estado_vehiculo='INACTIVO' WHERE id_vehiculo=?", id);
        invalid(null, () -> registerVehicle(c, u, UUID.randomUUID(), "c".repeat(64), "B2C345"));
        assertEquals(1, number(c, "SELECT count(*) FROM mycar.vehiculo"));
        assertEquals(1, number(c, "SELECT count(*) FROM mycar.lectura_kilometraje"));
        assertEquals("INTERDIARIO:PAUSADO", text(c, "SELECT frecuencia||':'||estado FROM mycar.config_recordatorio_km WHERE id_vehiculo=?", id));
        assertEquals(0, number(c, "SELECT count(*) FROM mycar.alerta"));
    }

    @Test void rf08FalloRecordatorioRevierteLosTresRegistros() throws Exception {
        long u = account(c); configureAccount(c, u);
        Savepoint start = c.setSavepoint();
        registerVehicle(c, u, UUID.randomUUID(), "a".repeat(64), "A1B234");
        assertThrows(SQLException.class, () -> exec(c, "INSERT INTO mycar.config_recordatorio_km(id_vehiculo,fecha_base_utc) SELECT id_vehiculo,clock_timestamp() FROM mycar.vehiculo"));
        c.rollback(start);
        assertEquals(0, number(c, "SELECT count(*) FROM mycar.vehiculo"));
        assertEquals(0, number(c, "SELECT count(*) FROM mycar.lectura_kilometraje"));
        assertEquals(0, number(c, "SELECT count(*) FROM mycar.operacion_idempotente"));
    }

    @Test void rf14SoloIncrementaYLecturaNoSeMuta() throws Exception {
        Vehicle v = fixture(c);
        invalid(null, () -> reading(c, v, 20000));
        invalid(null, () -> reading(c, v, 19000));
        reading(c, v, 21000);
        invalid(null, () -> exec(c, "UPDATE mycar.lectura_kilometraje SET valor_km=22000 WHERE id_lectura_km=?", v.reading));
        invalid(null, () -> exec(c, "UPDATE mycar.vehiculo SET id_usuario=? WHERE id_vehiculo=?", accountWith(c, "otro@example.test", LocalDate.now(LIMA).minusYears(30)), v.id));
        exec(c, "UPDATE mycar.vehiculo SET estado_vehiculo='INACTIVO' WHERE id_vehiculo=?", v.id);
        invalid(null, () -> reading(c, v, 22000));
    }

    @Test void rf17LecturaExactaDelMismoVehiculoYTipoActivo() throws Exception {
        Vehicle a = fixture(c);
        Vehicle b = otherFixture(c);
        invalid(null, () -> maintenance(c, b, a.reading, 20000, "MANTENIMIENTO_GENERAL", null));
        invalid(null, () -> maintenance(c, a, a.reading, 19000, "MANTENIMIENTO_GENERAL", null));
        long m = maintenance(c, a, a.reading, 20000, "MANTENIMIENTO_GENERAL", null);
        reading(c, a, 21000);
        assertEquals(20000, number(c, "SELECT km_servicio FROM mycar.mantenimiento WHERE id_mantenimiento=?", m));
        assertNull(text(c, "SELECT costo_total FROM mycar.mantenimiento WHERE id_mantenimiento=?", m));
        invalid(null, () -> maintenance(c, a, a.reading, 20000, "OTRO", null));
        exec(c, "UPDATE mycar.tipo_mantenimiento SET activo=FALSE WHERE codigo='MANTENIMIENTO_GENERAL'");
        invalid(null, () -> maintenance(c, a, a.reading, 20000, "MANTENIMIENTO_GENERAL", null));
    }

    @Test void rf17HistoricoNoAlteraOdometroYRecomendaciones() throws Exception {
        Vehicle v = fixture(c);
        long m = insertId(c, "INSERT INTO mycar.mantenimiento(id_vehiculo,id_tipo_mantenimiento,km_servicio,fecha_servicio,origen_km_servicio,proximo_km_sugerido) SELECT ?,id_tipo_mantenimiento,19000,?,'HISTORICO_MANUAL',19500 FROM mycar.tipo_mantenimiento WHERE codigo='MANTENIMIENTO_GENERAL' RETURNING id_mantenimiento", v.id, LocalDate.now(LIMA).minusDays(1));
        assertEquals(19000, number(c, "SELECT km_servicio FROM mycar.mantenimiento WHERE id_mantenimiento=?", m));
        assertEquals(20000, number(c, "SELECT valor_km FROM mycar.lectura_kilometraje WHERE id_vehiculo=?", v.id));
        invalid("23514", () -> exec(c, "UPDATE mycar.mantenimiento SET costo_total=-1 WHERE id_mantenimiento=?", m));
        invalid(null, () -> exec(c, "UPDATE mycar.mantenimiento SET fecha_servicio=? WHERE id_mantenimiento=?", LocalDate.now(LIMA).plusDays(1), m));
        assertEquals(0, number(c, "SELECT count(*) FROM mycar.alerta"));
    }

    @Test void rf17RepuestosRepetiblesConPropiedadYSnapshot() throws Exception {
        Vehicle a = fixture(c), b = otherFixture(c);
        long m = maintenance(c, a, a.reading, 20000, "MANTENIMIENTO_GENERAL", null);
        long r1 = part(c, a.user), r2 = part(c, a.user), foreign = part(c, b.user);
        exec(c, "INSERT INTO mycar.mantenimiento_repuesto VALUES (?,?,'Filtro guardado',1,NULL),(?,?,'Filtro guardado',2,10)", m, r1, m, r2);
        invalid(null, () -> exec(c, "INSERT INTO mycar.mantenimiento_repuesto VALUES (?,?,'Ajeno',1,NULL)", m, foreign));
        exec(c, "UPDATE mycar.repuesto SET nombre='Nombre nuevo' WHERE id_repuesto=?", r1);
        assertEquals("Filtro guardado", text(c, "SELECT nombre_snapshot FROM mycar.mantenimiento_repuesto WHERE id_mantenimiento=? AND id_repuesto=?", m, r1));
        Savepoint start = c.setSavepoint();
        long provisional = part(c, a.user);
        assertThrows(SQLException.class, () -> exec(c, "INSERT INTO mycar.mantenimiento_repuesto VALUES (?,?,'Inválido',0,NULL)", m, provisional));
        c.rollback(start);
        assertEquals(0, number(c, "SELECT count(*) FROM mycar.repuesto WHERE id_repuesto=?", provisional));
    }

    @Test void alertasLimitesTemporalesKmExactoYAmbos() throws Exception {
        LocalDate goal = LocalDate.of(2026, 11, 1);
        for (int[] sample : new int[][]{{-31, 1}, {-30, 2}, {-8, 2}, {-7, 3}, {0, 3}, {14, 3}, {15, 4}}) {
            String state = text(c, "SELECT mycar.estado_por_tiempo(?,?)", goal, goal.plusDays(sample[0]));
            assertEquals(sample[1], number(c, "SELECT mycar.prioridad_estado_alerta(?)", state));
        }
        assertEquals("PROGRAMADA", text(c, "SELECT mycar.estado_por_km(27000,1255,26874)"));
        assertEquals("PROXIMA", text(c, "SELECT mycar.estado_por_km(27000,1255,26875)"));
        assertEquals("PENDIENTE", text(c, "SELECT mycar.estado_por_km(27000,1255,27125)"));
        assertEquals("VENCIDA", text(c, "SELECT mycar.estado_por_km(27000,1255,27126)"));
        assertEquals("VENCIDA", text(c, "SELECT mycar.calcular_estado_alerta('AMBOS',?,?,27000,1255,27126)", goal, goal.minusDays(40)));
        assertEquals("PROGRAMADA", text(c, "SELECT mycar.estado_por_km(2147483647,2147483647,1)"));
    }

    @Test void mesesCalendarioDesdeBaseOriginal() throws Exception {
        assertEquals("2026-02-28", text(c, "SELECT (DATE '2026-01-31'+make_interval(months=>1))::date"));
        assertEquals("2024-02-29", text(c, "SELECT (DATE '2024-01-31'+make_interval(months=>1))::date"));
        assertEquals("2026-03-31", text(c, "SELECT (DATE '2026-01-31'+make_interval(months=>2))::date"));
    }

    @Test void normalizacionCompletaSinAlterarTextoVisible() throws Exception {
        assertEquals("strasse σ", text(c, "SELECT mycar.normalizar_servicio_otro(?)", "  ＳＴＲＡＳＳＥ\tς  "));
        assertEquals("strasse", text(c, "SELECT mycar.normalizar_servicio_otro('Straße')"));
        assertEquals("revisión de correa", text(c, "SELECT mycar.normalizar_servicio_otro(?)", " REVISIO\u0301N  de\tcorrea "));
        assertEquals("", text(c, "SELECT mycar.normalizar_servicio_otro('')"));
    }

    @Test void rf22ReferenciaUltimaNormalizadaYNoOtroVehiculo() throws Exception {
        Vehicle a = fixture(c), b = otherFixture(c);
        long first = maintenance(c, a, a.reading, 20000, "OTRO", "Straße  correa");
        long last = maintenance(c, a, a.reading, 20000, "OTRO", "STRASSE correa");
        invalid(null, () -> alert(c, a, "OTRO", "straße correa", first, "a".repeat(64)));
        invalid(null, () -> alert(c, b, "OTRO", "straße correa", last, "b".repeat(64)));
        long id = alert(c, a, "OTRO", "straße correa", last, "c".repeat(64));
        assertEquals("straße correa", text(c, "SELECT servicio_otro FROM mycar.alerta WHERE id_alerta=?", id));
    }

    @Test void rf22EquivalenteActivoNoDuplicaYBasesSeRevalidan() throws Exception {
        Vehicle v = fixture(c);
        long id = alert(c, v, "MANTENIMIENTO_GENERAL", null, null, "a".repeat(64));
        invalid("23505", () -> alert(c, v, "MANTENIMIENTO_GENERAL", null, null, "a".repeat(64)));
        invalid(null, () -> exec(c, "UPDATE mycar.alerta SET criterio='POR_KILOMETRAJE' WHERE id_alerta=?", id));
        invalid(null, () -> insertId(c, "INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,criterio,unidad_tiempo,cantidad_tiempo,fecha_base,fecha_objetivo,estado,firma_equivalencia) SELECT ?,id_tipo_mantenimiento,'POR_TIEMPO','SEMANAS',2,?,?, 'PENDIENTE',? FROM mycar.tipo_mantenimiento WHERE codigo='MANTENIMIENTO_GENERAL' RETURNING id_alerta", v.id, LocalDate.now(LIMA).minusDays(1), LocalDate.now(LIMA).plusDays(13), "b".repeat(64)));
        assertEquals(1, number(c, "SELECT count(*) FROM mycar.alerta"));
        assertEquals(1, number(c, "SELECT count(*) FROM mycar.historial_alerta"));
    }

    @Test void rf14NoSincronizaRf23CambiaSoloUnaVez() throws Exception {
        Vehicle v = fixture(c);
        long id = insertId(c, "INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,criterio,km_base,intervalo_km,km_objetivo,estado,firma_equivalencia) SELECT ?,id_tipo_mantenimiento,'POR_KILOMETRAJE',20000,1255,21255,'PROGRAMADA',? FROM mycar.tipo_mantenimiento WHERE codigo='MANTENIMIENTO_GENERAL' RETURNING id_alerta", v.id, "a".repeat(64));
        history(c, id, "PROGRAMADA");
        long proof = reading(c, v, 21381);
        assertEquals("PROGRAMADA", text(c, "SELECT estado FROM mycar.alerta WHERE id_alerta=?", id));
        assertEquals(1, number(c, "SELECT mycar.sincronizar_alertas_vehiculo(?,?)", v.id, v.user));
        assertEquals("VENCIDA", text(c, "SELECT estado FROM mycar.alerta WHERE id_alerta=?", id));
        assertEquals(proof, number(c, "SELECT id_lectura_vencida_evidencia FROM mycar.alerta WHERE id_alerta=?", id));
        assertEquals(0, number(c, "SELECT mycar.sincronizar_alertas_vehiculo(?,?)", v.id, v.user));
        assertEquals(2, number(c, "SELECT count(*) FROM mycar.historial_alerta WHERE id_alerta=?", id));
        invalid(null, () -> exec(c, "UPDATE mycar.historial_alerta SET motivo='Modificar' WHERE id_alerta=?", id));
    }

    @Test void rf23VeintinueveVsTreintaDiasDesdeEvidenciaNoDesdeCommit() throws Exception {
        Vehicle v = fixture(c);
        long day29 = historicTimeAlert(c, v, 29, "a".repeat(64));
        long day30 = historicTimeAlert(c, v, 30, "b".repeat(64));
        assertEquals(2, number(c, "SELECT mycar.sincronizar_alertas_vehiculo(?,?)", v.id, v.user));
        assertEquals("VENCIDA", text(c, "SELECT estado FROM mycar.alerta WHERE id_alerta=?", day29));
        assertEquals("ANULADA", text(c, "SELECT estado FROM mycar.alerta WHERE id_alerta=?", day30));
        assertEquals(1, number(c, "SELECT count(*) FROM mycar.historial_alerta WHERE id_alerta=? AND estado_anterior IS NOT NULL", day30));
        assertEquals(0, number(c, "SELECT mycar.sincronizar_alertas_vehiculo(?,?)", v.id, v.user));
        invalid(null, () -> exec(c, "UPDATE mycar.alerta SET fecha_vencida_verificable=fecha_vencida_verificable+1 WHERE id_alerta=?", day30));
        assertEquals(1, number(c, "SELECT count(*) FROM mycar.historial_alerta WHERE id_alerta=? AND motivo='VENCIMIENTO_30_DIAS'", day30));
    }

    @Test void rf23RechazaTitularAjenoYRollbackSiFallaHistorial() throws Exception {
        Vehicle a = fixture(c), b = otherFixture(c);
        invalid(null, () -> number(c, "SELECT mycar.sincronizar_alertas_vehiculo(?,?)", a.id, b.user));
        long id = historicTimeAlert(c, a, 29, "a".repeat(64));
        historicalFixture(c, () -> exec(c, "INSERT INTO mycar.historial_alerta(id_alerta,estado_anterior,estado_nuevo,motivo,version_destino) VALUES (?,'PROGRAMADA','VENCIDA','FALLO_SIMULADO',1)", id));
        invalid("23505", () -> number(c, "SELECT mycar.sincronizar_alertas_vehiculo(?,?)", a.id, a.user));
        assertEquals("PROGRAMADA", text(c, "SELECT estado FROM mycar.alerta WHERE id_alerta=?", id));
        assertEquals(0, number(c, "SELECT version_fila FROM mycar.alerta WHERE id_alerta=?", id));
    }

    @Test void rf23KmUsaPrimeraLecturaVerificableYNoLaUltima() throws Exception {
        Vehicle v = fixture(c);
        long id = historicKmAlert(c, v, "a".repeat(64));
        long first = historicalReading(c, v, 21381, 30);
        long later = historicalReading(c, v, 22000, 15);
        assertEquals(1, number(c, "SELECT mycar.sincronizar_alertas_vehiculo(?,?)", v.id, v.user));
        assertEquals("ANULADA", text(c, "SELECT estado FROM mycar.alerta WHERE id_alerta=?", id));
        assertEquals(first, number(c, "SELECT id_lectura_vencida_evidencia FROM mycar.alerta WHERE id_alerta=?", id));
        assertEquals(LocalDate.now(LIMA).minusDays(30).toString(), text(c, "SELECT fecha_vencida_verificable FROM mycar.alerta WHERE id_alerta=?", id));
        invalid(null, () -> exec(c, "UPDATE mycar.alerta SET id_lectura_vencida_evidencia=? WHERE id_alerta=?", later, id));
        invalid(null, () -> exec(c, "UPDATE mycar.alerta SET id_lectura_vencida_evidencia=? WHERE id_alerta=?", v.reading, id));
        assertEquals(2, number(c, "SELECT count(*) FROM mycar.historial_alerta WHERE id_alerta=?", id));
        assertEquals(0, number(c, "SELECT mycar.sincronizar_alertas_vehiculo(?,?)", v.id, v.user));
    }

    @Test void rf23NoAnulaPorAntiguedadSinEvidenciaKm() throws Exception {
        Vehicle v = fixture(c);
        long id = historicKmAlert(c, v, "a".repeat(64));
        assertEquals(0, number(c, "SELECT mycar.sincronizar_alertas_vehiculo(?,?)", v.id, v.user));
        assertEquals("PROGRAMADA", text(c, "SELECT estado FROM mycar.alerta WHERE id_alerta=?", id));
        assertNull(text(c, "SELECT fecha_vencida_verificable FROM mycar.alerta WHERE id_alerta=?", id));
    }

    @Test void rf23AmbosEligePrimeraEvidenciaYEmpateFavoreceTiempo() throws Exception {
        Vehicle v = fixture(c);
        prepareHistoricalInitial(c, v);
        long timeFirst = historicBothAlert(c, v, 50, "a".repeat(64));
        long tie = historicBothAlert(c, v, 45, "b".repeat(64));
        long kmFirst = historicBothAlert(c, v, 44, "c".repeat(64));
        long proof = historicalReading(c, v, 21381, 30);
        assertEquals(3, number(c, "SELECT mycar.sincronizar_alertas_vehiculo(?,?)", v.id, v.user));
        assertNull(text(c, "SELECT id_lectura_vencida_evidencia FROM mycar.alerta WHERE id_alerta=?", timeFirst));
        assertNull(text(c, "SELECT id_lectura_vencida_evidencia FROM mycar.alerta WHERE id_alerta=?", tie));
        assertEquals(proof, number(c, "SELECT id_lectura_vencida_evidencia FROM mycar.alerta WHERE id_alerta=?", kmFirst));
        assertEquals(3, number(c, "SELECT count(*) FROM mycar.alerta WHERE estado='ANULADA'"));
    }

    @Test void esquemaNoVacioSeRechazaSinModificarFilaExistente() throws Exception {
        String isolated = isolatedDatabase("preexistente");
        flyway(isolated, "1").migrate();
        try (Connection db = open(isolated)) {
            exec(db, "CREATE TABLE mycar.datos_existentes(id INTEGER PRIMARY KEY, nota TEXT)");
            exec(db, "INSERT INTO mycar.datos_existentes VALUES (1,'conservar')");
        }
        assertThrows(Exception.class, () -> flyway(isolated, null).migrate());
        try (Connection db = open(isolated)) {
            assertEquals("conservar", text(db, "SELECT nota FROM mycar.datos_existentes WHERE id=1"));
            assertEquals(0, number(db, "SELECT count(*) FROM pg_tables WHERE schemaname='mycar' AND tablename='usuario'"));
            assertEquals(1, number(db, "SELECT count(*) FROM mycar.flyway_schema_history WHERE version IS NOT NULL AND success"));
        }
    }

    @Test void concurrenciaFreeSoloUnAltaYReintentoMismaOperacion() throws Exception {
        String isolated = isolatedDatabase("free"); flyway(isolated, null).migrate();
        long u;
        try (Connection db = open(isolated)) { db.setAutoCommit(false); u = account(db); configureAccount(db, u); db.commit(); }
        long uid = u;
        List<Boolean> results = race(() -> concurrentRegistration(isolated, uid, "A1B234"), () -> concurrentRegistration(isolated, uid, "B2C345"));
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        try (Connection db = open(isolated)) {
            assertEquals(1, number(db, "SELECT count(*) FROM mycar.vehiculo"));
            assertEquals(1, number(db, "SELECT count(*) FROM mycar.lectura_kilometraje"));
            assertEquals(1, number(db, "SELECT count(*) FROM mycar.operacion_idempotente WHERE estado='CONFIRMADA'"));
        }
    }

    @Test void concurrenciaAlertasEquivalentesSoloUnaConHistorial() throws Exception {
        String isolated = isolatedDatabase("alertas"); flyway(isolated, null).migrate();
        Vehicle v;
        try (Connection db = open(isolated)) { db.setAutoCommit(false); v = fixture(db); db.commit(); }
        List<Boolean> results = race(() -> concurrentAlert(isolated, v), () -> concurrentAlert(isolated, v));
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        try (Connection db = open(isolated)) {
            assertEquals(1, number(db, "SELECT count(*) FROM mycar.alerta"));
            assertEquals(1, number(db, "SELECT count(*) FROM mycar.historial_alerta"));
        }
    }

    @Test void concurrenciaMismaOperacionRecuperaMismoVehiculoSinLimiteFree() throws Exception {
        String isolated = isolatedDatabase("reintento"); flyway(isolated, null).migrate();
        long u;
        try (Connection db = open(isolated)) { db.setAutoCommit(false); u = account(db); configureAccount(db, u); db.commit(); }
        UUID same = UUID.randomUUID();
        Callable<Boolean> attempt = () -> {
            try (Connection db = open(isolated)) {
                db.setAutoCommit(false);
                registerVehicle(db, u, same, "a".repeat(64), "A1B234");
                db.commit(); return true;
            }
        };
        assertEquals(List.of(true, true), race(attempt, attempt));
        try (Connection db = open(isolated)) {
            assertEquals(1, number(db, "SELECT count(*) FROM mycar.vehiculo"));
            assertEquals(1, number(db, "SELECT count(*) FROM mycar.operacion_idempotente"));
        }
    }

    @Test void concurrenciaLecturasConVersionSoloUnaConfirmacion() throws Exception {
        String isolated = isolatedDatabase("lecturas"); flyway(isolated, null).migrate();
        Vehicle v;
        try (Connection db = open(isolated)) { db.setAutoCommit(false); v = fixture(db); db.commit(); }
        List<Boolean> results = race(() -> concurrentReading(isolated, v, 21000), () -> concurrentReading(isolated, v, 22000));
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        try (Connection db = open(isolated)) {
            assertEquals(2, number(db, "SELECT count(*) FROM mycar.lectura_kilometraje"));
            assertEquals(1, number(db, "SELECT version_km FROM mycar.vehiculo WHERE id_vehiculo=?", v.id));
        }
    }

    @Test void smokeOriginalAdaptadoAHistoryDeFlyway() throws Exception {
        // El DO proporcionado tiene sus propios savepoints; el ROLLBACK externo pertenece al test.
        String script;
        try (var in = getClass().getResourceAsStream("/db/mvp/smoke_rollback.sql")) {
            assertNotNull(in); script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String body = script.substring(script.indexOf("DO $$"), script.lastIndexOf("ROLLBACK;"));
        exec(c, body);
    }

    // Estos helpers verifican contratos SQL transaccionales; NO son servicios REST implementados.
    record Vehicle(long user, long id, long reading) {}
    @FunctionalInterface interface SqlWork { void run() throws Exception; }
    void invalid(String state, SqlWork work) throws Exception {
        Savepoint save = c.setSavepoint();
        SQLException error = assertThrows(SQLException.class, work::run);
        c.rollback(save);
        if (state != null) assertEquals(state, error.getSQLState(), error.getMessage());
    }
    static String required(String key) { String v = System.getenv(key); if (v == null || v.isBlank()) throw new IllegalStateException("Falta " + key); return v; }
    static Connection open() throws SQLException { return open(url); }
    static Connection open(String target) throws SQLException { return DriverManager.getConnection(target, user, password); }
    static Flyway flyway(String target, String version) {
        var config = Flyway.configure().dataSource(target, user, password).schemas("mycar").defaultSchema("mycar")
                .locations("classpath:db/mvp").cleanDisabled(true).baselineOnMigrate(false);
        if (version != null) config.target(version);
        return config.load();
    }
    static PreparedStatement prepare(Connection db, String sql, Object... args) throws SQLException {
        PreparedStatement s = db.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
        return s;
    }
    static void exec(Connection db, String sql, Object... args) throws SQLException { try (var s = prepare(db, sql, args)) { s.execute(); } }
    static long number(Connection db, String sql, Object... args) throws SQLException {
        try (var s = prepare(db, sql, args); var r = s.executeQuery()) { assertTrue(r.next()); return r.getLong(1); }
    }
    static String text(Connection db, String sql, Object... args) throws SQLException {
        try (var s = prepare(db, sql, args); var r = s.executeQuery()) { assertTrue(r.next()); return r.getString(1); }
    }
    static long insertId(Connection db, String sql, Object... args) throws SQLException { return number(db, sql, args); }
    static long account(Connection db) throws SQLException { return accountWith(db, "evidencia@example.test", LocalDate.now(LIMA).minusYears(30)); }
    static long accountWith(Connection db, String email, LocalDate dob) throws SQLException {
        return insertId(db, "INSERT INTO mycar.usuario(correo,password_hash,nombre,apellido,fecha_nacimiento,consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version) VALUES (?,repeat('x',60),'Prueba','MVP',?,clock_timestamp(),'v1') RETURNING id_usuario", email, dob);
    }
    static void configureAccount(Connection db, long u) throws SQLException {
        exec(db, "INSERT INTO mycar.suscripcion(id_usuario,id_plan,estado) SELECT ?,id_plan,'ACTIVA' FROM mycar.plan WHERE codigo='FREE' AND activo", u);
        exec(db, "INSERT INTO mycar.preferencia_notificacion(id_usuario) VALUES (?)", u);
        exec(db, "INSERT INTO mycar.config_recordatorio_km_usuario(id_usuario) VALUES (?)", u);
    }
    static long session(Connection db, long u, UUID install, String hash) throws SQLException {
        return insertId(db, "INSERT INTO mycar.sesion(id_usuario,id_instalacion,refresh_token_hash,expira_utc) VALUES (?,?,?,clock_timestamp()+interval '1 day') RETURNING id_sesion", u, install, hash);
    }
    static long rawVehicle(Connection db, long u, String plate, String motor, String fuel, String detail) throws SQLException {
        return insertId(db, "INSERT INTO mycar.vehiculo(id_usuario,placa,marca,modelo,uso_vehiculo_codigo,anio_fabricacion,color,id_tipo_motor,id_combustible,tipo_motor_otro) VALUES (?,?,'Marca','Modelo','PARTICULAR',2020,'Azul',(SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo=?),(SELECT id_combustible FROM mycar.combustible WHERE codigo=?),?) RETURNING id_vehiculo", u, plate, motor, fuel, detail);
    }
    static long registerVehicle(Connection db, long u, UUID op, String hash, String plate) throws SQLException {
        exec(db, "INSERT INTO mycar.operacion_idempotente(id_operacion,id_usuario,tipo_operacion,hash_solicitud) VALUES (?,?,'REGISTRAR_VEHICULO',?) ON CONFLICT DO NOTHING", op, u, hash);
        try (var s = prepare(db, "SELECT id_usuario,tipo_operacion,hash_solicitud,estado,respuesta_json->>'idVehiculo' FROM mycar.operacion_idempotente WHERE id_operacion=? FOR UPDATE", op); var r = s.executeQuery()) {
            assertTrue(r.next());
            if (r.getLong(1) != u || !r.getString(2).equals("REGISTRAR_VEHICULO") || !r.getString(3).equals(hash)) throw new SQLException("CONFLICTO_IDEMPOTENCIA", "P0001");
            if (r.getString(4).equals("CONFIRMADA")) return Long.parseLong(r.getString(5));
        }
        exec(db, "SELECT id_usuario FROM mycar.usuario WHERE id_usuario=? AND estado_cuenta='ACTIVA' FOR NO KEY UPDATE", u);
        long limit = number(db, "SELECT p.max_vehiculos_registrados FROM mycar.suscripcion s JOIN mycar.plan p USING(id_plan) WHERE s.id_usuario=? AND s.estado='ACTIVA' AND p.codigo='FREE' AND p.activo", u);
        if (number(db, "SELECT count(*) FROM mycar.vehiculo WHERE id_usuario=?", u) >= limit) throw new SQLException("LIMITE_FREE", "P0001");
        long v = rawVehicle(db, u, plate, "COMBUSTION", "GASOLINA", null);
        exec(db, "UPDATE mycar.vehiculo SET es_principal=TRUE WHERE id_vehiculo=?", v);
        exec(db, "INSERT INTO mycar.lectura_kilometraje(id_vehiculo,valor_km,origen,id_operacion) VALUES (?,20000,'INICIAL',?)", v, op);
        exec(db, "INSERT INTO mycar.config_recordatorio_km(id_vehiculo,fecha_base_utc) SELECT ?,fecha_lectura_utc FROM mycar.lectura_kilometraje WHERE id_operacion=?", v, op);
        exec(db, "UPDATE mycar.operacion_idempotente SET estado='CONFIRMADA',respuesta_json=jsonb_build_object('idVehiculo',?::bigint) WHERE id_operacion=?", v, op);
        return v;
    }
    static Vehicle fixture(Connection db) throws SQLException {
        long u = account(db); configureAccount(db, u);
        long v = registerVehicle(db, u, UUID.randomUUID(), "a".repeat(64), "A1B234");
        return new Vehicle(u, v, number(db, "SELECT id_lectura_km FROM mycar.lectura_kilometraje WHERE id_vehiculo=?", v));
    }
    static Vehicle otherFixture(Connection db) throws SQLException {
        long u = accountWith(db, "otra@example.test", LocalDate.now(LIMA).minusYears(30)); configureAccount(db, u);
        long v = registerVehicle(db, u, UUID.randomUUID(), "b".repeat(64), "B2C345");
        return new Vehicle(u, v, number(db, "SELECT id_lectura_km FROM mycar.lectura_kilometraje WHERE id_vehiculo=?", v));
    }
    static long reading(Connection db, Vehicle v, int km) throws SQLException {
        UUID op = UUID.randomUUID();
        exec(db, "INSERT INTO mycar.operacion_idempotente(id_operacion,id_usuario,tipo_operacion,hash_solicitud) VALUES (?,?,'ACTUALIZAR_KM',?)", op, v.user, "d".repeat(64));
        long id = insertId(db, "INSERT INTO mycar.lectura_kilometraje(id_vehiculo,valor_km,origen,id_operacion) VALUES (?,?,'ACTUALIZACION',?) RETURNING id_lectura_km", v.id, km, op);
        exec(db, "UPDATE mycar.operacion_idempotente SET estado='CONFIRMADA',respuesta_json=jsonb_build_object('idLecturaKm',?::bigint) WHERE id_operacion=?", id, op);
        return id;
    }
    static long maintenance(Connection db, Vehicle v, long reading, int km, String type, String other) throws SQLException {
        return insertId(db, "INSERT INTO mycar.mantenimiento(id_vehiculo,id_tipo_mantenimiento,km_servicio,fecha_servicio,origen_km_servicio,id_lectura_km_referencia,servicio_otro) SELECT ?,id_tipo_mantenimiento,?,?,'LECTURA',?,? FROM mycar.tipo_mantenimiento WHERE codigo=? RETURNING id_mantenimiento", v.id, km, LocalDate.now(LIMA), reading, other, type);
    }
    static long part(Connection db, long u) throws SQLException { return insertId(db, "INSERT INTO mycar.repuesto(id_usuario,nombre) VALUES (?,'Filtro') RETURNING id_repuesto", u); }
    static long alert(Connection db, Vehicle v, String type, String other, Long ref, String signature) throws SQLException {
        long id = insertId(db, "INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,criterio,unidad_tiempo,cantidad_tiempo,fecha_base,fecha_objetivo,estado,firma_equivalencia,servicio_otro,id_mantenimiento_referencia) SELECT ?,id_tipo_mantenimiento,'POR_TIEMPO','SEMANAS',1,?,?,'PENDIENTE',?,?,? FROM mycar.tipo_mantenimiento WHERE codigo=? RETURNING id_alerta", v.id, LocalDate.now(LIMA), LocalDate.now(LIMA).plusDays(7), signature, other, ref, type);
        history(db, id, "PENDIENTE"); return id;
    }
    static void history(Connection db, long id, String state) throws SQLException { exec(db, "INSERT INTO mycar.historial_alerta(id_alerta,estado_nuevo,motivo,version_destino) VALUES (?,?,'INICIAL_RF22',0)", id, state); }
    static void historicalFixture(Connection db, SqlWork work) throws Exception {
        // Solo datos que habrían sido válidos al crearse en el pasado, en este contenedor desechable.
        // Nunca se ejecuta en migraciones ni se usa para las aserciones negativas de integridad.
        exec(db, "SET LOCAL session_replication_role=replica");
        try { work.run(); } finally { exec(db, "SET LOCAL session_replication_role=origin"); }
    }
    static long historicTimeAlert(Connection db, Vehicle v, int daysExpired, String signature) throws Exception {
        long[] id = new long[1]; LocalDate goal = LocalDate.now(LIMA).minusDays(daysExpired + 15L);
        historicalFixture(db, () -> id[0] = insertId(db, "INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,criterio,unidad_tiempo,cantidad_tiempo,fecha_base,fecha_objetivo,estado,firma_equivalencia,creada_utc) SELECT ?,id_tipo_mantenimiento,'POR_TIEMPO','SEMANAS',8,?,?,'PROGRAMADA',?,? FROM mycar.tipo_mantenimiento WHERE codigo='MANTENIMIENTO_GENERAL' RETURNING id_alerta", v.id, goal.minusWeeks(8), goal, signature, goal.minusWeeks(8).atStartOfDay(LIMA).toOffsetDateTime()));
        history(db, id[0], "PROGRAMADA"); return id[0];
    }
    static void prepareHistoricalInitial(Connection db, Vehicle v) throws Exception {
        historicalFixture(db, () -> {
            var date = LocalDate.now(LIMA).minusDays(130).atTime(12, 0).atZone(LIMA).toOffsetDateTime();
            exec(db, "UPDATE mycar.lectura_kilometraje SET fecha_lectura_utc=? WHERE id_lectura_km=?", date, v.reading);
            exec(db, "UPDATE mycar.config_recordatorio_km SET fecha_base_utc=? WHERE id_vehiculo=?", date, v.id);
        });
    }
    static long historicKmAlert(Connection db, Vehicle v, String signature) throws Exception {
        prepareHistoricalInitial(db, v);
        long[] id = new long[1];
        historicalFixture(db, () -> id[0] = insertId(db, "INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,criterio,km_base,intervalo_km,km_objetivo,estado,firma_equivalencia,creada_utc) SELECT ?,id_tipo_mantenimiento,'POR_KILOMETRAJE',20000,1255,21255,'PROGRAMADA',?,? FROM mycar.tipo_mantenimiento WHERE codigo='MANTENIMIENTO_GENERAL' RETURNING id_alerta", v.id, signature, LocalDate.now(LIMA).minusDays(70).atStartOfDay(LIMA).toOffsetDateTime()));
        history(db, id[0], "PROGRAMADA"); return id[0];
    }
    static long historicalReading(Connection db, Vehicle v, int km, int daysAgo) throws Exception {
        long id = reading(db, v, km);
        historicalFixture(db, () -> exec(db, "UPDATE mycar.lectura_kilometraje SET fecha_lectura_utc=? WHERE id_lectura_km=?", LocalDate.now(LIMA).minusDays(daysAgo).atTime(12, 0).atZone(LIMA).toOffsetDateTime(), id));
        return id;
    }
    static long historicBothAlert(Connection db, Vehicle v, int daysSinceGoal, String signature) throws Exception {
        long[] id = new long[1]; LocalDate goal = LocalDate.now(LIMA).minusDays(daysSinceGoal);
        historicalFixture(db, () -> id[0] = insertId(db, "INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,criterio,unidad_tiempo,cantidad_tiempo,fecha_base,fecha_objetivo,km_base,intervalo_km,km_objetivo,estado,firma_equivalencia,creada_utc) SELECT ?,id_tipo_mantenimiento,'AMBOS','SEMANAS',8,?,?,20000,1255,21255,'PROGRAMADA',?,? FROM mycar.tipo_mantenimiento WHERE codigo='MANTENIMIENTO_GENERAL' RETURNING id_alerta", v.id, goal.minusWeeks(8), goal, signature, goal.minusWeeks(8).atStartOfDay(LIMA).toOffsetDateTime()));
        history(db, id[0], "PROGRAMADA"); return id[0];
    }
    static String isolatedDatabase(String purpose) throws SQLException {
        String name = "mycar_mvp_test_" + purpose + "_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection db = open()) { exec(db, "CREATE DATABASE " + name); }
        return url.substring(0, url.lastIndexOf('/') + 1) + name;
    }
    static List<Boolean> race(Callable<Boolean> one, Callable<Boolean> two) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> job : List.of(one, two)) futures.add(pool.submit(() -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new TimeoutException(); return job.call(); }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            List<Boolean> result = new ArrayList<>();
            for (Future<Boolean> future : futures) result.add(future.get(20, TimeUnit.SECONDS));
            return result;
        }
    }
    static boolean concurrentRegistration(String target, long u, String plate) throws SQLException {
        try (Connection db = open(target)) {
            db.setAutoCommit(false);
            try { registerVehicle(db, u, UUID.randomUUID(), "a".repeat(64), plate); db.commit(); return true; }
            catch (SQLException ex) { db.rollback(); if (!"LIMITE_FREE".equals(ex.getMessage())) throw ex; return false; }
        }
    }
    static boolean concurrentAlert(String target, Vehicle v) throws SQLException {
        try (Connection db = open(target)) {
            db.setAutoCommit(false);
            try { alert(db, v, "MANTENIMIENTO_GENERAL", null, null, "a".repeat(64)); db.commit(); return true; }
            catch (SQLException ex) { db.rollback(); if (!"23505".equals(ex.getSQLState())) throw ex; return false; }
        }
    }
    static boolean concurrentReading(String target, Vehicle v, int km) throws SQLException {
        try (Connection db = open(target)) {
            db.setAutoCommit(false);
            try {
                if (number(db, "SELECT version_km FROM mycar.vehiculo WHERE id_vehiculo=? FOR UPDATE", v.id) != 0) { db.rollback(); return false; }
                reading(db, v, km);
                exec(db, "UPDATE mycar.vehiculo SET version_km=version_km+1 WHERE id_vehiculo=?", v.id);
                db.commit(); return true;
            } catch (SQLException ex) { db.rollback(); throw ex; }
        }
    }
}
