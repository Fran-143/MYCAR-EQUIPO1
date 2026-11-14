package backend.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/** Validación aislada V26 (no toca producción). Activar con MYCAR_V26_JDBC_URL. */
@EnabledIfEnvironmentVariable(named = "MYCAR_V26_JDBC_URL", matches = "jdbc:postgresql://.+")
class V26IsolatedMigrationTest {

    @Test
    void migraHastaV25LuegoV26ConTalleresManualesYFallaConDuplicados() throws Exception {
        String url = System.getenv("MYCAR_V26_JDBC_URL");
        String user = System.getenv().getOrDefault("MYCAR_V26_USER", "mycar");
        String pass = System.getenv().getOrDefault("MYCAR_V26_PASSWORD", "mycar");

        Flyway.configure().dataSource(url, user, pass)
                .schemas("mycar").defaultSchema("mycar")
                .locations("classpath:db/mvp").cleanDisabled(true).target("25").load()
                .migrate();

        long t1;
        long t2;
        long m;
        try (var c = DriverManager.getConnection(url, user, pass)) {
            c.setAutoCommit(false);
            long u = insertUser(c);
            t1 = insertTallerManual(c, u, "Taller A", "999111222");
            t2 = insertTallerManual(c, u, "Taller B", "999333444");
            long v = insertVehicle(c, u);
            m = insertMant(c, v, t1);
            c.commit();
        }

        Flyway fw = Flyway.configure().dataSource(url, user, pass)
                .schemas("mycar").defaultSchema("mycar")
                .locations("classpath:db/mvp").cleanDisabled(true).load();
        assertEquals(1, fw.migrate().migrationsExecuted, "solo V26");
        assertEquals("26", fw.info().current().getVersion().toString());

        try (var c = DriverManager.getConnection(url, user, pass)) {

            try (var st = c.createStatement(); var r = st.executeQuery(
                    "SELECT origen, id_taller_global, tiene_whatsapp FROM mycar.taller WHERE id_taller=" + t1)) {
                assertTrue(r.next());
                assertEquals("MANUAL", r.getString(1));
                assertNull(r.getObject(2));
                assertFalse(r.getBoolean(3));
            }
            try (var st = c.createStatement(); var r = st.executeQuery(
                    "SELECT count(*) FROM mycar.taller_global WHERE es_demo")) {
                assertTrue(r.next());
                assertEquals(2, r.getLong(1));
            }
            try (var st = c.createStatement(); var r = st.executeQuery(
                    "SELECT count(*) FROM mycar.taller WHERE id_taller_global IS NOT NULL")) {
                assertTrue(r.next());
                assertEquals(0, r.getLong(1), "demo no se publica como taller de usuario");
            }
            try (var st = c.createStatement(); var r = st.executeQuery(
                    "SELECT id_taller FROM mycar.mantenimiento WHERE id_mantenimiento=" + m)) {
                assertTrue(r.next());
                assertEquals(t1, r.getLong(1));
            }
            try (var st = c.createStatement(); var r = st.executeQuery(
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema='mycar' AND table_name='alerta' AND column_name='id_taller_contacto'")) {
                assertTrue(r.next());
                assertEquals(1, r.getLong(1));
            }
            assertEquals(t2, t2);
        }

        String urlDup = System.getenv("MYCAR_V26_DUP_JDBC_URL");
        if (urlDup != null && !urlDup.isBlank()) {
            Flyway fw2to25 = Flyway.configure().dataSource(urlDup, user, pass)
                    .schemas("mycar").defaultSchema("mycar")
                    .locations("classpath:db/mvp").cleanDisabled(true).target("25").load();
            fw2to25.migrate();
            try (var c = DriverManager.getConnection(urlDup, user, pass)) {
                c.setAutoCommit(false);
                long u = insertUser(c);
                insertTallerManual(c, u, "Dup1", "555000111");
                insertTallerManual(c, u, "Dup2", "555000111");
                c.commit();
            }
            Flyway fw2full = Flyway.configure().dataSource(urlDup, user, pass)
                    .schemas("mycar").defaultSchema("mycar")
                    .locations("classpath:db/mvp").cleanDisabled(true).load();
            var ex = assertThrows(Exception.class, () -> fw2full.migrate());
            String msg = String.valueOf(ex.getMessage()) + " " + String.valueOf(ex.getCause());
            assertTrue(msg.toLowerCase().contains("duplicado"),
                    () -> "esperado fail-fast por telefono duplicado: " + msg);
        }
    }

    static long insertUser(java.sql.Connection c) throws SQLException {
        try (var ps = c.prepareStatement(
                "INSERT INTO mycar.usuario(correo,password_hash,nombre,apellido,fecha_nacimiento,consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version) VALUES (?,repeat('x',60),'V26','Test',CURRENT_DATE-interval '30 years',clock_timestamp(),'v1') RETURNING id_usuario")) {
            ps.setString(1, "v26_" + System.nanoTime() + "@test.local");
            try (var r = ps.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    static long insertTallerManual(java.sql.Connection c, long u, String nombre, String tel) throws SQLException {
        try (var ps = c.prepareStatement(
                "INSERT INTO mycar.taller(id_usuario,nombre,telefono,estado) VALUES (?,?,?,'ACTIVO') RETURNING id_taller")) {
            ps.setLong(1, u);
            ps.setString(2, nombre);
            ps.setString(3, tel);
            try (var r = ps.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    static long insertVehicle(java.sql.Connection c, long u) throws SQLException {
        try (var ps = c.prepareStatement(
                "INSERT INTO mycar.vehiculo(id_usuario,placa,marca,modelo,uso_vehiculo_codigo,anio_fabricacion,color,id_tipo_motor,id_combustible) " +
                        "VALUES (?,'A1B234','Toyota','Yaris','PARTICULAR',2018,'Azul'," +
                        "(SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='COMBUSTION')," +
                        "(SELECT id_combustible FROM mycar.combustible WHERE codigo='GASOLINA')) RETURNING id_vehiculo")) {
            ps.setLong(1, u);
            try (var r = ps.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    static long insertMant(java.sql.Connection c, long v, long t) throws SQLException {
        try (var ps = c.prepareStatement(
                "INSERT INTO mycar.mantenimiento(id_vehiculo,id_tipo_mantenimiento,km_servicio,fecha_servicio,origen_km_servicio,id_taller) " +
                        "SELECT ?, id_tipo_mantenimiento, 10000, CURRENT_DATE, 'HISTORICO_MANUAL', ? " +
                        "FROM mycar.tipo_mantenimiento WHERE activo LIMIT 1 RETURNING id_mantenimiento")) {
            ps.setLong(1, v);
            ps.setLong(2, t);
            try (var r = ps.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }
}

