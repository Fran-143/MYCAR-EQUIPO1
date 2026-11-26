package backend.cuentas;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CuentasRf55DetalleYRf03FixTest {

    @DynamicPropertySource
    static void localOnly(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:5432/mycar_mvp");
        r.add("spring.datasource.username", () -> "mycar");
        r.add("spring.datasource.password", () -> "root");
        r.add("mycar.seguridad.exigir-datasource-local", () -> "true");
        r.add("mycar.recuperacion.desarrollo", () -> "true");
        r.add("mycar.auth.login-auto-alta", () -> "false");
        r.add("mycar.auth.jwt-secret-base64", () -> {
            try {
                var p = java.nio.file.Path.of(System.getenv("LOCALAPPDATA"), "MyCar", "desarrollo-local", "autenticacion.properties");
                for (String line : java.nio.file.Files.readAllLines(p)) {
                    if (line.startsWith("mycar.auth.jwt-secret-base64=")) {
                        return line.substring("mycar.auth.jwt-secret-base64=".length()).trim();
                    }
                }
            } catch (Exception ignored) { }
            return "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
        });
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CuentaAdminService admin;
    @Autowired RestriccionRecuperacionService restriccion;

    private long userId;

    @BeforeEach
    void assumeLocal() throws Exception {
        String u = jdbc.getDataSource().getConnection().getMetaData().getURL();
        Assumptions.assumeTrue(u.contains("127.0.0.1") || u.contains("localhost"));
        Assumptions.assumeFalse(u.contains("neon.tech"));
        var ids = jdbc.query("SELECT id_usuario FROM mycar.usuario WHERE lower(correo)='rf.activa@mycar.test'",
                (rs, n) -> rs.getLong(1));
        Assumptions.assumeFalse(ids.isEmpty());
        userId = ids.getFirst();
    }

    @Test
    void detalleNoFallaConRestriccionSinBloqueoHasta() {
        jdbc.update("""
                UPDATE mycar.usuario SET estado_cuenta='ACTIVA', tipo_bloqueo=NULL,
                  bloqueo_admin_hasta_utc=NULL, login_bloqueo_hasta_utc=NULL WHERE id_usuario=?
                """, userId);
        jdbc.update("""
                INSERT INTO mycar.restriccion_recuperacion(id_usuario, finalidad, solicitudes_ciclo, nivel_bloqueo, bloqueado_hasta_utc, actualizado_utc)
                VALUES (?,'RECUPERAR_CONTRASENA',1,0,NULL,clock_timestamp())
                ON CONFLICT (id_usuario) DO UPDATE
                  SET solicitudes_ciclo=1, nivel_bloqueo=0, bloqueado_hasta_utc=NULL, actualizado_utc=clock_timestamp()
                """, userId);
        Map<String, Object> det = assertDoesNotThrow(() -> admin.detalle(userId));
        assertEquals("ACTIVA", det.get("estado"));
        assertNotNull(det.get("restriccionRecuperacion"));
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) det.get("restriccionRecuperacion");
        assertNull(r.get("bloqueadoHastaUtc"));
        assertEquals(false, r.get("bloqueado"));
        assertFalse(det.containsKey("password_hash"));
    }

    @Test
    void solicitudDuranteBloqueoNoAmpliaNiGeneraNuevoNivel() {
        Instant now = Instant.parse("2026-11-21T20:00:00Z");
        Instant hasta = now.plus(30, ChronoUnit.MINUTES);
        jdbc.update("""
                INSERT INTO mycar.restriccion_recuperacion(id_usuario, finalidad, solicitudes_ciclo, nivel_bloqueo, bloqueado_hasta_utc, actualizado_utc)
                VALUES (?,'RECUPERAR_CONTRASENA',0,2,?,?)
                ON CONFLICT (id_usuario) DO UPDATE
                  SET solicitudes_ciclo=0, nivel_bloqueo=2, bloqueado_hasta_utc=EXCLUDED.bloqueado_hasta_utc, actualizado_utc=EXCLUDED.actualizado_utc
                """, userId, OffsetDateTime.ofInstant(hasta, ZoneOffset.UTC), OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        CuentaException ex = assertThrows(CuentaException.class,
                () -> restriccion.registrarSolicitudOBloquear(userId, "rf.activa@mycar.test", now.plusSeconds(5), "t"));
        assertEquals(CuentaException.Reason.BLOQUEO_RECUPERACION, ex.reason());
        var estado = restriccion.estado(userId).orElseThrow();
        assertEquals(2, estado.nivelBloqueo());
        assertEquals(hasta, estado.bloqueadoHasta());
    }
}
