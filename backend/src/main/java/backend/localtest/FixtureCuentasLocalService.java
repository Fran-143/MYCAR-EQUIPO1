package backend.localtest;

import backend.cuentas.CuentaEstados;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "mycar.local-test.enabled", havingValue = "true")
public class FixtureCuentasLocalService {
    public static final String PASSWORD = "PruebaCuentas35!";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final boolean enabled;

    public FixtureCuentasLocalService(
            JdbcTemplate jdbc,
            @Qualifier("registrationPasswordEncoder") PasswordEncoder passwords,
            @Value("${mycar.local-test.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.enabled = enabled;
    }

    @Transactional
    public Map<String, Object> restablecerUsuariosRf() {
        LocalTestGuard.requireEnabled(enabled);
        Instant now = Instant.now();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("passwordCompartida", PASSWORD);
        out.put("items", List.of(
                ensure("rf.activa@mycar.test", "Activa", "RF", "USUARIO", CuentaEstados.ACTIVA, null, now),
                ensure("rf.suspendida@mycar.test", "Suspendida", "RF", "USUARIO", CuentaEstados.SUSPENDIDA, null, now),
                ensureBloqueoLogin("rf.bloq.login@mycar.test", now),
                ensureBloqueoRecup("rf.bloq.recup@mycar.test", now),
                ensureBloqueoAdmin("rf.bloq.admin@mycar.test", now),
                ensurePendiente("rf.pendiente@mycar.test", now),
                ensureEliminadaConstancia(now),
                ensure("rf.admin@mycar.test", "Admin", "RF", "ADMIN", CuentaEstados.ACTIVA, null, now)
        ));
        out.put("resultado", "OK");
        return out;
    }

    @Transactional
    public Map<String, Object> prepararPendienteRf51() {
        LocalTestGuard.requireEnabled(enabled);
        return ensurePendiente("rf.pendiente@mycar.test", Instant.now());
    }

    @Transactional
    public Map<String, Object> prepararLimiteEliminacion30d() {
        LocalTestGuard.requireEnabled(enabled);
        Instant now = Instant.now();
        long id = upsertUser("rf.limite30@mycar.test", "Limite", "RF", "USUARIO", CuentaEstados.ACTIVA);
        clearSolicitudes(id);
        jdbc.update("""
                INSERT INTO mycar.solicitud_eliminacion(
                    id_usuario, estado, motivo_codigo, solicitada_utc, vence_utc, origen, cancelada_utc)
                VALUES (?,'CANCELADA','FIXTURE',?,?, 'TITULAR', ?)
                """, id,
                utc(now.minus(5, ChronoUnit.DAYS)),
                utc(now.plus(25, ChronoUnit.DAYS)),
                utc(now.minus(4, ChronoUnit.DAYS)));
        return Map.of(
                "correo", "rf.limite30@mycar.test",
                "estado", CuentaEstados.ACTIVA,
                "nota", "Solicitud CANCELADA hace 5 días; nueva solicitud debe rechazarse por límite 30d.",
                "password", PASSWORD);
    }

    private Map<String, Object> ensure(
            String correo, String nombre, String apellido, String rol, String estado,
            Instant bloqueoAdminHasta, Instant now) {
        long id = upsertUser(correo, nombre, apellido, rol, estado);
        clearRestricciones(id);
        clearSolicitudes(id);
        jdbc.update("""
                UPDATE mycar.usuario SET tipo_bloqueo=NULL, bloqueo_admin_hasta_utc=?,
                  login_bloqueo_hasta_utc=NULL, eliminacion_programada_utc=NULL,
                  motivo_estado='FIXTURE_LOCAL', observacion_estado=NULL,
                  password_hash=?
                 WHERE id_usuario=?
                """, bloqueoAdminHasta == null ? null : utc(bloqueoAdminHasta),
                passwords.encode(PASSWORD), id);
        return Map.of("correo", correo, "estado", estado, "password", PASSWORD);
    }

    private Map<String, Object> ensureBloqueoLogin(String correo, Instant now) {
        Map<String, Object> base = ensure(correo, "BloqLogin", "RF", "USUARIO", CuentaEstados.ACTIVA, null, now);
        long id = idOf(correo);
        jdbc.update("""
                UPDATE mycar.usuario SET login_bloqueo_hasta_utc=?, login_fallos_desde_utc=?
                 WHERE id_usuario=?
                """, utc(now.plus(10, ChronoUnit.MINUTES)), utc(now), id);
        return new LinkedHashMap<>(base) {{
            put("nota", "Bloqueo login 10 min vigente; estado base ACTIVA.");
        }};
    }

    private Map<String, Object> ensureBloqueoRecup(String correo, Instant now) {
        Map<String, Object> base = ensure(correo, "BloqRecup", "RF", "USUARIO", CuentaEstados.ACTIVA, null, now);
        long id = idOf(correo);
        jdbc.update("DELETE FROM mycar.restriccion_recuperacion WHERE id_usuario=?", id);
        jdbc.update("""
                INSERT INTO mycar.restriccion_recuperacion(
                    id_usuario, finalidad, solicitudes_ciclo, nivel_bloqueo, bloqueado_hasta_utc, actualizado_utc)
                VALUES (?,'RECUPERAR_CONTRASENA',3,1,?,?)
                """, id, utc(now.plus(1, ChronoUnit.HOURS)), utc(now));
        return new LinkedHashMap<>(base) {{
            put("nota", "Restricción recuperación ~1h; estado base ACTIVA (visible BLOQUEADA).");
        }};
    }

    private Map<String, Object> ensureBloqueoAdmin(String correo, Instant now) {
        long id = upsertUser(correo, "BloqAdmin", "RF", "USUARIO", CuentaEstados.BLOQUEADA);
        clearRestricciones(id);
        clearSolicitudes(id);
        jdbc.update("""
                UPDATE mycar.usuario SET tipo_bloqueo='ADMINISTRATIVA',
                  bloqueo_admin_hasta_utc=?, login_bloqueo_hasta_utc=NULL,
                  password_hash=?, motivo_estado='FIXTURE_LOCAL'
                 WHERE id_usuario=?
                """, utc(now.plus(2, ChronoUnit.HOURS)), passwords.encode(PASSWORD), id);
        return Map.of("correo", correo, "estado", CuentaEstados.BLOQUEADA, "password", PASSWORD);
    }

    private Map<String, Object> ensurePendiente(String correo, Instant now) {
        long id = upsertUser(correo, "Pendiente", "RF", "USUARIO", CuentaEstados.PENDIENTE_ELIMINACION);
        clearRestricciones(id);
        jdbc.update("DELETE FROM mycar.verificacion_reactivacion_asistida WHERE id_usuario=?", id);
        clearSolicitudes(id);
        Instant vence = now.plus(29, ChronoUnit.DAYS);
        jdbc.update("""
                UPDATE mycar.usuario SET estado_cuenta='PENDIENTE_ELIMINACION',
                  eliminacion_programada_utc=?, password_hash=?, motivo_estado='FIXTURE_LOCAL',
                  tipo_bloqueo=NULL, bloqueo_admin_hasta_utc=NULL, login_bloqueo_hasta_utc=NULL
                 WHERE id_usuario=?
                """, utc(vence), passwords.encode(PASSWORD), id);
        jdbc.update("""
                INSERT INTO mycar.solicitud_eliminacion(
                    id_usuario, estado, motivo_codigo, solicitada_utc, vence_utc, origen)
                VALUES (?,'VIGENTE','FIXTURE',?,?,'TITULAR')
                """, id, utc(now), utc(vence));
        jdbc.update("""
                UPDATE mycar.sesion SET estado_sesion='REVOCADA', revocada_utc=clock_timestamp()
                 WHERE id_usuario=? AND estado_sesion='VIGENTE'
                """, id);
        return Map.of(
                "correo", correo,
                "estado", CuentaEstados.PENDIENTE_ELIMINACION,
                "password", PASSWORD,
                "venceUtc", vence.toString());
    }

    private Map<String, Object> ensureEliminadaConstancia(Instant now) {
        String correo = "eliminado+elim-seedlocal0001@invalid.mycar.local";
        Long existing = jdbc.query("""
                SELECT id_usuario FROM mycar.usuario WHERE lower(correo)=lower(?)
                """, (r, n) -> r.getLong(1), correo).stream().findFirst().orElse(null);
        long id;
        if (existing == null) {
            id = jdbc.queryForObject("""
                    INSERT INTO mycar.usuario(correo,password_hash,estado_cuenta,nombre,apellido,fecha_nacimiento,
                      consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version,codigo_rol,
                      constancia_eliminacion)
                    VALUES (?,?,'ELIMINADA','Eliminado','RF',DATE '1990-01-01',clock_timestamp(),'1.0','USUARIO',?)
                    RETURNING id_usuario
                    """, Long.class, correo, passwords.encode(UUID.randomUUID() + "Xx1!"), "ELIM-SEED-LOCAL-0001");
        } else {
            id = existing;
            jdbc.update("""
                    UPDATE mycar.usuario SET estado_cuenta='ELIMINADA', constancia_eliminacion='ELIM-SEED-LOCAL-0001',
                      nombre='Eliminado', apellido='RF' WHERE id_usuario=?
                    """, id);
        }
        return Map.of("correo", correo, "estado", CuentaEstados.ELIMINADA, "constancia", "ELIM-SEED-LOCAL-0001");
    }

    private long upsertUser(String correo, String nombre, String apellido, String rol, String estado) {
        LocalTestGuard.requireDestinatario(correo);
        Long existing = jdbc.query("""
                SELECT id_usuario FROM mycar.usuario WHERE lower(correo)=lower(?)
                """, (r, n) -> r.getLong(1), correo).stream().findFirst().orElse(null);
        if (existing != null) {
            jdbc.update("""
                    UPDATE mycar.usuario SET nombre=?, apellido=?, codigo_rol=?, estado_cuenta=?, password_hash=?
                     WHERE id_usuario=?
                    """, nombre, apellido, rol, estado, passwords.encode(PASSWORD), existing);
            return existing;
        }
        return jdbc.queryForObject("""
                INSERT INTO mycar.usuario(correo,password_hash,estado_cuenta,nombre,apellido,fecha_nacimiento,
                  consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version,codigo_rol)
                VALUES (?,?,?,?,?,DATE '1990-01-01',clock_timestamp(),'1.0',?)
                RETURNING id_usuario
                """, Long.class, correo, passwords.encode(PASSWORD), estado, nombre, apellido, rol);
    }

    private long idOf(String correo) {
        return jdbc.queryForObject(
                "SELECT id_usuario FROM mycar.usuario WHERE lower(correo)=lower(?)", Long.class, correo);
    }

    private void clearSolicitudes(long id) {
        jdbc.update("DELETE FROM mycar.verificacion_reactivacion_asistida WHERE id_usuario=?", id);
        jdbc.update("DELETE FROM mycar.solicitud_eliminacion WHERE id_usuario=?", id);
    }

    private void clearRestricciones(long id) {
        jdbc.update("DELETE FROM mycar.evento_restriccion_recuperacion WHERE id_usuario=?", id);
        jdbc.update("DELETE FROM mycar.restriccion_recuperacion WHERE id_usuario=?", id);
        jdbc.update("""
                UPDATE mycar.codigo_recuperacion SET estado='INVALIDADO'
                 WHERE id_usuario=? AND estado='ACTIVO'
                """, id);
    }

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}
