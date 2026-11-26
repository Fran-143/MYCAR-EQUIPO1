package backend.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/** Validación aislada V27. Activar con MYCAR_V27_JDBC_URL. */
@EnabledIfEnvironmentVariable(named = "MYCAR_V27_JDBC_URL", matches = "jdbc:postgresql://.+")
class V27IsolatedMigrationTest {

    @Test
    void migraHastaV26LuegoV27SoatYAlertaCategoria() throws Exception {
        String url = System.getenv("MYCAR_V27_JDBC_URL");
        String user = System.getenv().getOrDefault("MYCAR_V27_USER", "mycar");
        String pass = System.getenv().getOrDefault("MYCAR_V27_PASSWORD", "mycar");

        Flyway.configure().dataSource(url, user, pass)
                .schemas("mycar").defaultSchema("mycar")
                .locations("classpath:db/mvp").cleanDisabled(true).target("26").load()
                .migrate();

        long vehiculo;
        try (var c = DriverManager.getConnection(url, user, pass)) {
            c.setAutoCommit(false);
            long u = V26IsolatedMigrationTest.insertUser(c);
            vehiculo = V26IsolatedMigrationTest.insertVehicle(c, u);
            c.commit();
        }

        Flyway fw = Flyway.configure().dataSource(url, user, pass)
                .schemas("mycar").defaultSchema("mycar")
                .locations("classpath:db/mvp").cleanDisabled(true).load();
        assertEquals(1, fw.migrate().migrationsExecuted, "solo V27");
        assertEquals("27", fw.info().current().getVersion().toString());

        try (var c = DriverManager.getConnection(url, user, pass)) {
            try (var st = c.createStatement(); var r = st.executeQuery(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema='mycar' AND table_name='soat'")) {
                assertTrue(r.next());
                assertEquals(1, r.getLong(1));
            }
            try (var st = c.createStatement(); var r = st.executeQuery(
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema='mycar' "
                            + "AND table_name='alerta' AND column_name='id_soat'")) {
                assertTrue(r.next());
                assertEquals(1, r.getLong(1));
            }
            try (var ps = c.prepareStatement(
                    "INSERT INTO mycar.soat(id_vehiculo,aseguradora,numero_poliza,fecha_inicio,fecha_vencimiento,vigencia) "
                            + "VALUES (?, 'Rimac', 'POL-1', CURRENT_DATE, CURRENT_DATE + 365, 'ACTUAL') RETURNING id_soat")) {
                ps.setLong(1, vehiculo);
                long idSoat;
                try (var r = ps.executeQuery()) {
                    r.next();
                    idSoat = r.getLong(1);
                }
                try (var ins = c.prepareStatement(
                        "INSERT INTO mycar.alerta(id_vehiculo,categoria,id_soat,criterio,origen_calculo,"
                                + "unidad_tiempo,cantidad_tiempo,fecha_base,fecha_objetivo,estado,firma_equivalencia) "
                                + "VALUES (?,'SOAT',?,'POR_TIEMPO','DESDE_HOY','DIAS',30,"
                                + "CURRENT_DATE+335,CURRENT_DATE+365,"
                                + "mycar.calcular_estado_alerta('POR_TIEMPO',CURRENT_DATE+365,"
                                + "(clock_timestamp() AT TIME ZONE 'America/Lima')::date,NULL,NULL,0),"
                                + "repeat('a',64))")) {
                    ins.setLong(1, vehiculo);
                    ins.setLong(2, idSoat);
                    assertEquals(1, ins.executeUpdate());
                }
            }
        }
    }
}