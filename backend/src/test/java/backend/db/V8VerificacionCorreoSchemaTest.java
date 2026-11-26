package backend.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BD desechable únicamente. Cadena V1–V10: V8 Nayu + V10 Elsa/Francesca (reemplaza tablas RF01).
 * Activar: -Dmycar.db.tests=true con MYCAR_TEST_JDBC_URL=jdbc:postgresql://host/mycar_mvp_test_* .
 */
@EnabledIfSystemProperty(named = "mycar.db.tests", matches = "true")
class V8VerificacionCorreoSchemaTest {

    @Test
    void v10DejaEsquemaFrancescaTrasV8Nayu() throws Exception {
        String bootstrap = required("MYCAR_TEST_JDBC_URL");
        String user = required("MYCAR_TEST_USER");
        String password = required("MYCAR_TEST_PASSWORD");
        assertTrue(bootstrap.matches("jdbc:postgresql://[^/]+/mycar_mvp_test_[a-z0-9_]+"),
                "Solo bases desechables mycar_mvp_test_*");

        String name = "mycar_mvp_test_v8rf01_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(bootstrap, user, password)) {
            try (var s = admin.createStatement()) {
                s.execute("CREATE DATABASE " + name);
            }
        }
        String url = bootstrap.substring(0, bootstrap.lastIndexOf('/') + 1) + name;
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(url, user, password)
                    .schemas("mycar")
                    .defaultSchema("mycar")
                    .locations("classpath:db/mvp")
                    .cleanDisabled(true)
                    .load();
            var first = flyway.migrate();
            assertTrue(first.migrationsExecuted >= 10, "Se esperan al menos V1–V10");
            flyway.validate();
            var second = flyway.migrate();
            assertEquals(0, second.migrationsExecuted, "Rearranque no debe repetir DDL");

            try (Connection c = DriverManager.getConnection(url, user, password)) {
                assertTrue(columnExists(c, "usuario", "correo_verificado_utc"));
                for (String table : Set.of(
                        "control_verificacion",
                        "operacion_verificacion",
                        "emision_codigo",
                        "intento_verificacion")) {
                    assertTrue(tableExists(c, table), table);
                }
                // Tras V10: índices Francesca, no los de V8 Nayu (proposito).
                assertTrue(indexExists(c, "uq_control_correo"));
                assertTrue(indexExists(c, "uq_emision_vigente"));
                assertFalse(indexExists(c, "uq_control_verificacion_proposito_clave"));
                assertTrue(indexExists(c, "uq_usuario_correo_normalizado"));
                assertTrue(columnExists(c, "control_verificacion", "finalidad"));
                assertFalse(columnExists(c, "control_verificacion", "proposito"));

                // Cuenta previa (V2+): correo_verificado_utc queda NULL sin backfill.
                seedUsuarioPrevio(c);
                assertEquals(0, scalarLong(c,
                        "SELECT count(*) FROM mycar.usuario WHERE correo_verificado_utc IS NOT NULL"));

                UUID idOrigen = UUID.randomUUID();
                UUID idCorreo = UUID.randomUUID();
                byte[] sujeto = bytes(32, (byte) 1);
                byte[] credencial = bytes(32, (byte) 2);
                exec(c, """
                        INSERT INTO mycar.control_verificacion(
                          id_control, finalidad, tipo, clave_sujeto, credencial_hash,
                          creado_utc, ultimo_uso_utc)
                        VALUES (?, 'REGISTRO', 'ORIGEN', ?, ?, clock_timestamp(), clock_timestamp())
                        """, idOrigen, sujeto, credencial);
                exec(c, """
                        INSERT INTO mycar.control_verificacion(
                          id_control, finalidad, tipo, clave_sujeto, correo_normalizado,
                          creado_utc, ultimo_uso_utc)
                        VALUES (?, 'REGISTRO', 'CORREO', ?, 'demo@example.test',
                          clock_timestamp(), clock_timestamp())
                        """, idCorreo, bytes(32, (byte) 3));

                UUID op = UUID.randomUUID();
                byte[] secreto = bytes(32, (byte) 4);
                exec(c, """
                        INSERT INTO mycar.operacion_verificacion(
                          id_operacion, finalidad, id_control_origen, secreto_hash, estado,
                          creada_utc, expira_utc, retener_hasta_utc)
                        SELECT ?, 'REGISTRO', ?, ?, 'ABIERTA',
                          now_ts, now_ts + interval '24 hours', now_ts + interval '72 hours'
                        FROM (SELECT clock_timestamp() AS now_ts) t
                        """, op, idOrigen, secreto);

                UUID emision = UUID.randomUUID();
                exec(c, """
                        INSERT INTO mycar.emision_codigo(
                          id_emision, id_operacion, finalidad, generacion, id_control_correo,
                          estado_codigo, estado_envio, estado_cupo, proveedor,
                          clave_idempotencia_proveedor, creada_utc, reserva_hasta_utc)
                        SELECT ?, ?, 'REGISTRO', 1, ?,
                          'RESERVADO', 'PENDIENTE', 'RESERVADO', 'PENDIENTE',
                          ?, now_ts, now_ts + interval '120 seconds'
                        FROM (SELECT clock_timestamp() AS now_ts) t
                        """, emision, op, idCorreo, "idem-" + emision);

                UUID solicitud = UUID.randomUUID();
                exec(c, """
                        INSERT INTO mycar.intento_verificacion(
                          id_operacion, id_solicitud, finalidad, accion, id_emision,
                          huella_solicitud, clave_huella_version, resultado, http_status,
                          revision_resultado, ocurrido_utc)
                        VALUES (?, ?, 'REGISTRO', 'EMITIR', ?,
                          ?, 1, 'EMISION_CREADA', 200, 0, clock_timestamp())
                        """, op, solicitud, emision, bytes(32, (byte) 5));

                SQLException dup = assertThrows(SQLException.class, () -> exec(c, """
                        INSERT INTO mycar.intento_verificacion(
                          id_operacion, id_solicitud, finalidad, accion, id_emision,
                          huella_solicitud, clave_huella_version, resultado, http_status,
                          revision_resultado, ocurrido_utc)
                        VALUES (?, ?, 'REGISTRO', 'EMITIR', ?,
                          ?, 1, 'EMISION_CREADA', 200, 0, clock_timestamp())
                        """, op, solicitud, emision, bytes(32, (byte) 6)));
                assertEquals("23505", dup.getSQLState());

                SQLException badTipo = assertThrows(SQLException.class, () -> exec(c, """
                        INSERT INTO mycar.control_verificacion(
                          id_control, finalidad, tipo, clave_sujeto, correo_normalizado,
                          creado_utc, ultimo_uso_utc)
                        VALUES (?, 'REGISTRO', 'SMS', ?, 'otro@example.test',
                          clock_timestamp(), clock_timestamp())
                        """, UUID.randomUUID(), bytes(32, (byte) 7)));
                assertEquals("23514", badTipo.getSQLState());
            }
        } finally {
            try (Connection admin = DriverManager.getConnection(bootstrap, user, password);
                 var s = admin.createStatement()) {
                s.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
            } catch (SQLException ignored) {
                // best-effort cleanup
            }
        }
    }

    private static void seedUsuarioPrevio(Connection c) throws SQLException {
        exec(c, """
                INSERT INTO mycar.usuario(
                  correo, password_hash, nombre, apellido, fecha_nacimiento,
                  consentimiento_privacidad_fecha_utc, consentimiento_privacidad_version)
                SELECT 'previo@example.test', repeat('x',60), 'Previo', 'Cuenta', DATE '1990-01-01',
                  clock_timestamp(), 'v1'
                WHERE NOT EXISTS (
                  SELECT 1 FROM mycar.usuario WHERE lower(btrim(correo))='previo@example.test')
                """);
        assertNull(scalarObject(c,
                "SELECT correo_verificado_utc FROM mycar.usuario WHERE lower(btrim(correo))='previo@example.test'"));
    }

    private static String required(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) throw new IllegalStateException("Falta " + key);
        return v;
    }

    private static byte[] bytes(int n, byte fill) {
        byte[] b = new byte[n];
        java.util.Arrays.fill(b, fill);
        return b;
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        try (var s = c.prepareStatement(
                "SELECT 1 FROM pg_tables WHERE schemaname='mycar' AND tablename=?")) {
            s.setString(1, table);
            try (var r = s.executeQuery()) {
                return r.next();
            }
        }
    }

    private static boolean columnExists(Connection c, String table, String column) throws SQLException {
        try (var s = c.prepareStatement("""
                SELECT 1 FROM information_schema.columns
                WHERE table_schema='mycar' AND table_name=? AND column_name=?
                """)) {
            s.setString(1, table);
            s.setString(2, column);
            try (var r = s.executeQuery()) {
                return r.next();
            }
        }
    }

    private static boolean indexExists(Connection c, String index) throws SQLException {
        try (var s = c.prepareStatement(
                "SELECT 1 FROM pg_indexes WHERE schemaname='mycar' AND indexname=?")) {
            s.setString(1, index);
            try (var r = s.executeQuery()) {
                return r.next();
            }
        }
    }

    private static long scalarLong(Connection c, String sql, Object... args) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            bind(s, args);
            try (var r = s.executeQuery()) {
                assertTrue(r.next());
                return r.getLong(1);
            }
        }
    }

    private static Object scalarObject(Connection c, String sql, Object... args) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            bind(s, args);
            try (var r = s.executeQuery()) {
                assertTrue(r.next());
                return r.getObject(1);
            }
        }
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            bind(s, args);
            s.execute();
        }
    }

    private static void bind(java.sql.PreparedStatement s, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof byte[] bytes) {
                s.setBytes(i + 1, bytes);
            } else if (a instanceof UUID uuid) {
                s.setObject(i + 1, uuid, Types.OTHER);
            } else {
                s.setObject(i + 1, a);
            }
        }
    }
}
