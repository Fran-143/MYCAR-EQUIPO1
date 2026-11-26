package backend.cuentas;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CuentasRf07LimiteYRf51AsistidaTest {

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
    @Autowired EliminacionCuentaService eliminacion;
    @Autowired ReactivacionCuentaService reactivacion;
    @Autowired ReactivacionAsistidaService asistida;
    @Autowired @Qualifier("registrationPasswordEncoder") PasswordEncoder passwords;

    private long adminId;
    private long userId;

    @BeforeEach
    void setup() throws Exception {
        var meta = jdbc.getDataSource().getConnection().getMetaData();
        String u = meta.getURL();
        Assumptions.assumeTrue(u.contains("127.0.0.1") || u.contains("localhost"), "Solo Postgres local");
        Assumptions.assumeFalse(u.contains("neon.tech"), "Neon prohibido");
        Integer v40 = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.flyway_schema_history WHERE version='40' AND success", Integer.class);
        Assumptions.assumeTrue(v40 != null && v40 > 0, "V40 requerida");

        adminId = ensureUser("it.admin.limite@mycar.test", "Admin", "Lim", "ADMIN", "ACTIVA");
        userId = ensureUser("it.limite.elim@mycar.test", "Limite", "User", "USUARIO", "ACTIVA");
        resetUser();
    }

    @Test
    void rf07_rechazaSegundaSolicitudDentroDe30Dias() {
        Instant t0 = Instant.parse("2026-11-21T15:00:00Z");
        insertOtp(userId, "111111", CuentaEstados.FINALIDAD_ELIMINAR, t0);
        Map<String, Object> ok = eliminacion.confirmar(userId, "111111", "PRUEBA", null, t0);
        assertEquals("SOLICITUD_REGISTRADA", ok.get("resultado"));

        // Recuperar al día siguiente (RF51 propio)
        Instant t1 = t0.plus(1, ChronoUnit.DAYS);
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta='ACTIVA', eliminacion_programada_utc=NULL WHERE id_usuario=?", userId);
        jdbc.update("""
                UPDATE mycar.solicitud_eliminacion SET estado='CANCELADA', cancelada_utc=?
                 WHERE id_usuario=? AND estado='VIGENTE'
                """, OffsetDateTime.ofInstant(t1, ZoneOffset.UTC), userId);

        Instant t2 = t0.plus(2, ChronoUnit.DAYS);
        CuentaException ex = assertThrows(CuentaException.class, () -> eliminacion.iniciar(userId, t2));
        assertEquals("LIMITE_ELIMINACION_30D", ex.codigo());
        assertTrue(ex.fields().containsKey("proximaSolicitudUtc"));

        // Tras 30 días desde la solicitud original sí permite (confirmar aplica el mismo control).
        Instant t3 = t0.plus(30, ChronoUnit.DAYS).plusSeconds(1);
        insertOtp(userId, "444444", CuentaEstados.FINALIDAD_ELIMINAR, t3);
        Map<String, Object> allow = eliminacion.confirmar(userId, "444444", "NUEVA", null, t3);
        assertEquals("SOLICITUD_REGISTRADA", allow.get("resultado"));
    }

    @Test
    void rf51_reactivacionAsistidaRequiereTitularYNoReiniciaLimite() {
        Instant now = Instant.parse("2026-11-21T16:00:00Z");
        insertOtp(userId, "222222", CuentaEstados.FINALIDAD_ELIMINAR, now);
        eliminacion.confirmar(userId, "222222", "PRUEBA", null, now);

        CuentaException sinVerif = assertThrows(CuentaException.class,
                () -> asistida.reactivarAdmin(adminId, userId, "Soporte validó", null, now));
        assertEquals("FORBIDDEN", sinVerif.codigo());

        Map<String, Object> ini = asistida.iniciarVerificacion(adminId, userId, now);
        assertEquals("VERIFICACION_INICIADA", ini.get("resultado"));
        assertFalse(Boolean.TRUE.equals(ini.get("titularVerificado")));
        assertNull(ini.get("codigoDesarrollo"));

        // Reabrir / re-llamar no debe emitir otro OTP mientras esté vigente.
        Map<String, Object> keep = asistida.iniciarVerificacion(adminId, userId, now.plusSeconds(30));
        assertEquals("VERIFICACION_VIGENTE", keep.get("resultado"));
        assertEquals(false, keep.get("reiniciada"));
        assertEquals("PENDIENTE_VERIFICACION", keep.get("fase"));
        assertEquals(ini.get("idVerificacion"), keep.get("idVerificacion"));

        Map<String, Object> estadoPend = asistida.estadoParaAdmin(userId, now.plusSeconds(30));
        assertEquals("PENDIENTE_VERIFICACION", estadoPend.get("fase"));

        insertOtp(userId, "333333", CuentaEstados.FINALIDAD_VERIFICAR_REACTIVACION_ADMIN, now);
        Map<String, Object> conf = asistida.verificarCodigoAdmin(adminId, userId, "333333", now);
        assertEquals("TITULAR_VERIFICADO", conf.get("resultado"));
        assertEquals("TITULAR_VERIFICADO", conf.get("fase"));

        Map<String, Object> estado = asistida.estadoParaAdmin(userId, now);
        assertTrue(Boolean.TRUE.equals(estado.get("titularVerificado")));
        assertTrue(Boolean.TRUE.equals(estado.get("puedeReactivar")));
        assertEquals("TITULAR_VERIFICADO", estado.get("fase"));

        Map<String, Object> rec = asistida.reactivarAdmin(adminId, userId, "Asistencia verificada", "ticket-1", now);
        assertEquals("CUENTA_RECUPERADA", rec.get("resultado"));
        assertEquals("ACTIVA", rec.get("estado"));
        assertEquals("REACTIVADA", asistida.estadoParaAdmin(userId, now).get("fase"));

        // El límite de 30 días sigue vigente tras reactivación admin
        Instant later = now.plus(3, ChronoUnit.DAYS);
        CuentaException lim = assertThrows(CuentaException.class, () -> eliminacion.iniciar(userId, later));
        assertEquals("LIMITE_ELIMINACION_30D", lim.codigo());
    }

    @Test
    void mensajesLoginSinCambiarPolitica() {
        assertTrue(backend.autenticacion.AuthenticationFailure.loginTemporarilyLocked("2026-11-21T20:00:00Z")
                .getMessage().contains("unos minutos"));
        assertFalse(backend.autenticacion.AuthenticationFailure.loginTemporarilyLocked("2026-11-21T20:00:00Z")
                .getMessage().contains("2026-11-21T20:00:00Z"));
        assertTrue(backend.autenticacion.AuthenticationFailure.blocked("2026-11-21T20:00:00Z")
                .getMessage().contains("Administración"));
        assertFalse(backend.autenticacion.AuthenticationFailure.blocked("2026-11-21T20:00:00Z")
                .getMessage().contains("2026-11-21T20:00:00Z"));
    }

    private void resetUser() {
        jdbc.update("""
                UPDATE mycar.usuario SET estado_cuenta='ACTIVA', tipo_bloqueo=NULL, bloqueo_admin_hasta_utc=NULL,
                  login_bloqueo_hasta_utc=NULL, eliminacion_programada_utc=NULL, motivo_estado=NULL,
                  observacion_estado=NULL WHERE id_usuario=?
                """, userId);
        jdbc.update("DELETE FROM mycar.verificacion_reactivacion_asistida WHERE id_usuario=?", userId);
        // Limpieza de fixture IT: no borra historial de cuentas reales.
        jdbc.update("DELETE FROM mycar.solicitud_eliminacion WHERE id_usuario=?", userId);
        jdbc.update("UPDATE mycar.codigo_recuperacion SET estado='INVALIDADO' WHERE id_usuario=? AND estado='ACTIVO'", userId);
    }

    private long ensureUser(String correo, String nombre, String apellido, String rol, String estado) {
        Long id = jdbc.query("""
                SELECT id_usuario FROM mycar.usuario WHERE lower(correo)=lower(?)
                """, (r, n) -> r.getLong(1), correo).stream().findFirst().orElse(null);
        if (id != null) {
            jdbc.update("""
                    UPDATE mycar.usuario SET password_hash=?, estado_cuenta=?, codigo_rol=?, nombre=?, apellido=?
                     WHERE id_usuario=?
                    """, passwords.encode(PASS), estado, rol, nombre, apellido, id);
            return id;
        }
        return jdbc.queryForObject("""
                INSERT INTO mycar.usuario(correo,password_hash,estado_cuenta,nombre,apellido,fecha_nacimiento,
                  consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version,codigo_rol)
                VALUES (?,?,?,?,?,DATE '1990-01-01',clock_timestamp(),'1.0',?)
                RETURNING id_usuario
                """, Long.class, correo, passwords.encode(PASS), estado, nombre, apellido, rol);
    }

    private void insertOtp(long uid, String code, String finalidad, Instant now) {
        jdbc.update("""
                UPDATE mycar.codigo_recuperacion SET estado='INVALIDADO'
                 WHERE id_usuario=? AND finalidad=? AND estado='ACTIVO'
                """, uid, finalidad);
        jdbc.update("""
                INSERT INTO mycar.codigo_recuperacion(id_usuario, codigo_hash, estado, expira_utc, creado_utc, finalidad)
                VALUES (?,?,'ACTIVO',?,?,?)
                """, uid, hash(code),
                OffsetDateTime.ofInstant(now.plus(10, ChronoUnit.MINUTES), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC), finalidad);
    }

    private static String hash(String code) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
