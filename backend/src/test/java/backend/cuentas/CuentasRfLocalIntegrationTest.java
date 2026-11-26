package backend.cuentas;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas locales RF55/56/07/51/RF03 sobre PostgreSQL local (mycar_mvp).
 * No usa Neon. Se salta si el datasource no es local.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CuentasRfLocalIntegrationTest {

    private static final String PASS = "PruebaCuentas35!";

    @DynamicPropertySource
    static void localOnly(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:5432/mycar_mvp");
        r.add("spring.datasource.username", () -> System.getenv().getOrDefault("POSTGRES_USER", "mycar"));
        r.add("spring.datasource.password", () -> System.getenv().getOrDefault("POSTGRES_PASSWORD", "root"));
        r.add("mycar.seguridad.exigir-datasource-local", () -> "true");
        r.add("mycar.recuperacion.desarrollo", () -> "true");
        r.add("mycar.auth.login-auto-alta", () -> "false");
        r.add("spring.flyway.enabled", () -> "true");
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
        r.add("server.port", () -> "0");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CuentaAdminService admin;
    @Autowired EliminacionCuentaService eliminacion;
    @Autowired ReactivacionCuentaService reactivacion;
    @Autowired RestriccionRecuperacionService restriccion;
    @Autowired EjecutarEliminacionVencida job;
    @Autowired SesionRevocacion sesiones;
    @Autowired @Qualifier("registrationPasswordEncoder") PasswordEncoder passwords;

    private long adminId;
    private long targetId;

    @BeforeEach
    void requireLocalAndSeed() throws Exception {
        var meta = jdbc.getDataSource().getConnection().getMetaData();
        String u = meta.getURL();
        Assumptions.assumeTrue(u.contains("127.0.0.1") || u.contains("localhost"), "Solo Postgres local");
        Assumptions.assumeFalse(u.contains("neon.tech"), "Neon prohibido");
        Integer v39 = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.flyway_schema_history WHERE version='39' AND success", Integer.class);
        Assumptions.assumeTrue(v39 != null && v39 > 0, "V39 requerida");

        adminId = ensureUser("it.admin.cuentas@mycar.test", "Admin", "IT", "ADMIN", "ACTIVA");
        targetId = ensureUser("it.target.cuentas@mycar.test", "Target", "IT", "USUARIO", "ACTIVA");
        resetTarget();
    }

    private void resetTarget() {
        jdbc.update("""
                UPDATE mycar.usuario SET estado_cuenta='ACTIVA', tipo_bloqueo=NULL, bloqueo_admin_hasta_utc=NULL,
                  login_bloqueo_hasta_utc=NULL, eliminacion_programada_utc=NULL, motivo_estado=NULL,
                  observacion_estado=NULL WHERE id_usuario=?
                """, targetId);
        jdbc.update("""
                UPDATE mycar.solicitud_eliminacion SET estado='CANCELADA', cancelada_utc=clock_timestamp()
                 WHERE id_usuario=? AND estado='VIGENTE'
                """, targetId);
        jdbc.update("DELETE FROM mycar.restriccion_recuperacion WHERE id_usuario=?", targetId);
        jdbc.update("DELETE FROM mycar.evento_restriccion_recuperacion WHERE id_usuario=?", targetId);
        jdbc.update("DELETE FROM mycar.alerta_seguridad_enviada WHERE id_usuario=?", targetId);
        jdbc.update("""
                UPDATE mycar.codigo_recuperacion SET estado='INVALIDADO'
                 WHERE id_usuario=? AND estado='ACTIVO'
                """, targetId);
        jdbc.update("""
                UPDATE mycar.sesion SET estado_sesion='REVOCADA', revocada_utc=clock_timestamp()
                 WHERE id_usuario=? AND estado_sesion='VIGENTE'
                """, targetId);
    }

    private long ensureUser(String correo, String nombre, String apellido, String rol, String estado) {
        var existing = jdbc.query("SELECT id_usuario FROM mycar.usuario WHERE lower(correo)=lower(?)",
                (rs, n) -> rs.getLong(1), correo);
        if (!existing.isEmpty()) {
            jdbc.update("UPDATE mycar.usuario SET codigo_rol=?, estado_cuenta=?, password_hash=? WHERE id_usuario=?",
                    rol, estado, passwords.encode(PASS), existing.getFirst());
            return existing.getFirst();
        }
        return jdbc.queryForObject("""
                INSERT INTO mycar.usuario(correo,password_hash,estado_cuenta,nombre,apellido,fecha_nacimiento,
                  consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version,codigo_rol)
                VALUES (?,?,?,?,?,DATE '1990-01-01',clock_timestamp(),'1.0',?)
                RETURNING id_usuario
                """, Long.class, correo, passwords.encode(PASS), estado, nombre, apellido, rol);
    }

    private static String sha256(String code) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private void insertOtp(long userId, String code, String finalidad, Instant now) {
        jdbc.update("""
                UPDATE mycar.codigo_recuperacion SET estado='INVALIDADO'
                 WHERE id_usuario=? AND finalidad=? AND estado='ACTIVO'
                """, userId, finalidad);
        jdbc.update("""
                INSERT INTO mycar.codigo_recuperacion(id_usuario,codigo_hash,estado,expira_utc,creado_utc,finalidad)
                VALUES (?,?,'ACTIVO',?,?,?)
                """, userId, sha256(code), utc(now.plus(10, ChronoUnit.MINUTES)), utc(now), finalidad);
    }

    @Test
    @Order(1)
    void rf55_listadoFiltrosDetallePrivacidad() {
        Map<String, Object> page = admin.listar("it.target", "ACTIVA", "USUARIO", 0, 20, true);
        assertTrue(((Number) page.get("total")).intValue() >= 1);
        @SuppressWarnings("unchecked")
        var items = (java.util.List<Map<String, Object>>) page.get("items");
        assertFalse(items.isEmpty());
        Map<String, Object> det = admin.detalle(targetId);
        assertEquals("ACTIVA", det.get("estado"));
        assertFalse(det.containsKey("password_hash"));
        assertFalse(String.valueOf(det).toLowerCase().contains("password_hash"));
    }

    @Test
    @Order(2)
    void rf56_suspenderBloquearDesbloquearAuditoriaYNoAutosuspender() {
        assertThrows(CuentaException.class, () -> admin.suspender(adminId, adminId, "auto", null));
        admin.suspender(adminId, targetId, "prueba suspension", "obs");
        assertEquals("SUSPENDIDA", jdbc.queryForObject(
                "SELECT estado_cuenta FROM mycar.usuario WHERE id_usuario=?", String.class, targetId));
        Integer audit = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.historial_estado_cuenta WHERE id_usuario_afectado=? AND accion='SUSPENDER'",
                Integer.class, targetId);
        assertTrue(audit != null && audit >= 1);
        admin.levantarSuspension(adminId, targetId, "levantar suspension prueba");
        admin.bloquear(adminId, targetId, "bloqueo admin", 30, null);
        assertEquals("BLOQUEADA", jdbc.queryForObject(
                "SELECT estado_cuenta FROM mycar.usuario WHERE id_usuario=?", String.class, targetId));
        admin.desbloquear(adminId, targetId, "fin bloqueo admin");
        assertEquals("ACTIVA", jdbc.queryForObject(
                "SELECT estado_cuenta FROM mycar.usuario WHERE id_usuario=?", String.class, targetId));
    }

    @Test
    @Order(3)
    void rf07_solicitudOtpYTreintaDias() {
        Instant now = Instant.parse("2026-11-21T18:00:00Z");
        insertOtp(targetId, "123456", CuentaEstados.FINALIDAD_ELIMINAR, now);
        Map<String, Object> conf = eliminacion.confirmar(targetId, "123456", "PRIVACIDAD", null, now);
        assertEquals("SOLICITUD_REGISTRADA", conf.get("resultado"));
        assertEquals("PENDIENTE_ELIMINACION", jdbc.queryForObject(
                "SELECT estado_cuenta FROM mycar.usuario WHERE id_usuario=?", String.class, targetId));
        Instant vence = jdbc.queryForObject(
                "SELECT vence_utc FROM mycar.solicitud_eliminacion WHERE id_usuario=? AND estado='VIGENTE'",
                java.sql.Timestamp.class, targetId).toInstant();
        assertEquals(30, ChronoUnit.DAYS.between(now.truncatedTo(ChronoUnit.DAYS), vence.truncatedTo(ChronoUnit.DAYS)));
    }

    @Test
    @Order(4)
    void rf51_recuperacionValidaYVencida() {
        Instant now = Instant.parse("2026-11-21T18:00:00Z");
        jdbc.update("""
                UPDATE mycar.usuario SET estado_cuenta='PENDIENTE_ELIMINACION', eliminacion_programada_utc=?
                 WHERE id_usuario=?
                """, utc(now.plus(10, ChronoUnit.DAYS)), targetId);
        jdbc.update("""
                UPDATE mycar.solicitud_eliminacion SET estado='CANCELADA', cancelada_utc=clock_timestamp()
                 WHERE id_usuario=? AND estado='VIGENTE'
                """, targetId);
        jdbc.update("""
                INSERT INTO mycar.solicitud_eliminacion(id_usuario,estado,solicitada_utc,vence_utc,origen)
                VALUES (?,'VIGENTE',?,?, 'TITULAR')
                """, targetId, utc(now.minus(1, ChronoUnit.DAYS)), utc(now.plus(10, ChronoUnit.DAYS)));
        insertOtp(targetId, "654321", CuentaEstados.FINALIDAD_REACTIVAR, now);
        Map<String, Object> ok = reactivacion.confirmar("it.target.cuentas@mycar.test", "654321", now);
        assertEquals("CUENTA_RECUPERADA", ok.get("resultado"));

        jdbc.update("UPDATE mycar.usuario SET estado_cuenta='PENDIENTE_ELIMINACION' WHERE id_usuario=?", targetId);
        jdbc.update("""
                UPDATE mycar.solicitud_eliminacion SET estado='CANCELADA', cancelada_utc=clock_timestamp()
                 WHERE id_usuario=? AND estado='VIGENTE'
                """, targetId);
        jdbc.update("""
                INSERT INTO mycar.solicitud_eliminacion(id_usuario,estado,solicitada_utc,vence_utc,origen)
                VALUES (?,'VIGENTE',?,?, 'TITULAR')
                """, targetId, utc(now.minus(40, ChronoUnit.DAYS)), utc(now.minus(1, ChronoUnit.DAYS)));
        assertThrows(CuentaException.class,
                () -> reactivacion.iniciar("it.target.cuentas@mycar.test", PASS, now));
    }

    @Test
    @Order(5)
    void rf03_cuatroNivelesProgresivos() {
        Instant cursor = Instant.parse("2026-11-21T10:00:00Z");
        int[] expectedHours = {1, 2, 4, 24, 24};
        for (int nivel = 1; nivel <= 5; nivel++) {
            Instant cycleStart = cursor;
            for (int i = 0; i < 3; i++) {
                restriccion.registrarSolicitudOBloquear(
                        targetId, "it.target.cuentas@mycar.test", cycleStart.plusSeconds(i), "t");
            }
            var estado = restriccion.estado(targetId).orElseThrow();
            assertEquals(nivel, estado.nivelBloqueo());
            assertNotNull(estado.bloqueadoHasta());
            Instant ref = cycleStart.plusSeconds(2);
            assertTrue(estado.bloqueado(ref));
            long minutes = ChronoUnit.MINUTES.between(ref, estado.bloqueadoHasta());
            long expectedMinutes = expectedHours[nivel - 1] * 60L;
            assertTrue(Math.abs(minutes - expectedMinutes) <= 2,
                    "nivel=" + nivel + " minutes=" + minutes + " expected~" + expectedMinutes);
            cursor = estado.bloqueadoHasta().plusSeconds(1);
        }
        Integer alertas = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.alerta_seguridad_enviada WHERE id_usuario=? AND tipo_evento='RECUPERACION_BLOQUEO'",
                Integer.class, targetId);
        assertTrue(alertas != null && alertas >= 1);
    }

    @Test
    @Order(6)
    void eliminacionDefinitivaIdempotente() {
        Instant now = Instant.parse("2026-11-21T18:00:00Z");
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta='PENDIENTE_ELIMINACION' WHERE id_usuario=?", targetId);
        jdbc.update("""
                UPDATE mycar.solicitud_eliminacion SET estado='CANCELADA', cancelada_utc=clock_timestamp()
                 WHERE id_usuario=? AND estado='VIGENTE'
                """, targetId);
        jdbc.update("""
                INSERT INTO mycar.solicitud_eliminacion(id_usuario,estado,solicitada_utc,vence_utc,origen)
                VALUES (?,'VIGENTE',?,?, 'TITULAR')
                """, targetId, utc(now.minus(31, ChronoUnit.DAYS)), utc(now.minus(1, ChronoUnit.DAYS)));
        assertTrue(((Number) job.ejecutar(now, 10).get("procesadas")).intValue() >= 1);
        assertEquals("ELIMINADA", jdbc.queryForObject(
                "SELECT estado_cuenta FROM mycar.usuario WHERE id_usuario=?", String.class, targetId));
        assertEquals(0, ((Number) job.ejecutar(now, 10).get("procesadas")).intValue());
    }

    @Test
    @Order(7)
    void sesionesRevocadas() {
        Instant now = Instant.now();
        String hash = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        hash = hash.substring(0, 64);
        jdbc.update("""
                INSERT INTO mycar.sesion(id_usuario,id_instalacion,refresh_token_hash,expira_utc,estado_sesion)
                VALUES (?,?,?,?,'VIGENTE')
                """, targetId, UUID.randomUUID(), hash, utc(now.plus(1, ChronoUnit.DAYS)));
        assertTrue(sesiones.revocarTodas(targetId, now) >= 1);
        Integer vigentes = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.sesion WHERE id_usuario=? AND estado_sesion='VIGENTE'", Integer.class, targetId);
        assertEquals(0, vigentes);
    }
}
