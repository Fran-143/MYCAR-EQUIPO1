package backend.registro.persistence;

import backend.registro.RegistrationData;
import backend.registro.RegistrationException;
import backend.registro.RegistrationStore;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
public class JdbcRegistrationStore implements RegistrationStore {
    private final JdbcTemplate jdbc;

    public JdbcRegistrationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean emailExists(String normalizedEmail) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM mycar.usuario WHERE lower(btrim(correo))=?)",
                Boolean.class, normalizedEmail));
    }

    @Override
    public long createAccount(RegistrationData data, String passwordHash, Instant acceptedAt) {
        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO mycar.usuario
                      (nombre,apellido,correo,fecha_nacimiento,password_hash,estado_cuenta,codigo_rol,
                       consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version)
                    VALUES (?,?,?,?,?,'ACTIVA','USUARIO',?,?) RETURNING id_usuario
                    """, Long.class, data.nombre(), data.apellido(), data.correo(), data.fechaNacimiento(),
                    passwordHash, OffsetDateTime.ofInstant(acceptedAt, ZoneOffset.UTC), data.versionPrivacidad());
            if (id == null) throw RegistrationException.unavailable();
            // No hardcoded plan ID. Lock the selected plan against concurrent changes during registration.
            Long plan = jdbc.query("SELECT id_plan FROM mycar.plan WHERE codigo='FREE' AND activo FOR SHARE",
                    rs -> rs.next() ? rs.getLong(1) : null);
            if (plan == null) throw RegistrationException.unavailable();
            jdbc.update("INSERT INTO mycar.suscripcion(id_usuario,id_plan,estado,fin_utc) VALUES (?,?,'ACTIVA',NULL)", id, plan);
            jdbc.update("INSERT INTO mycar.config_recordatorio_km_usuario(id_usuario,estado_recordatorios) VALUES (?,'PAUSADO')", id);
            jdbc.update("""
                    INSERT INTO mycar.preferencia_notificacion
                      (id_usuario,mantenimiento_on,soat_on,kilometraje_on,permiso_dispositivo)
                    VALUES (?,TRUE,TRUE,TRUE,'NO_SOLICITADO')
                    """, id);
            return id;
        } catch (DuplicateKeyException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())
                        && sql.getMessage() != null && sql.getMessage().contains("uq_usuario_correo_normalizado")) {
                    throw RegistrationException.emailUnavailable();
                }
            }
            throw RegistrationException.unavailable();
        } catch (DataAccessException ex) {
            // Do not expose driver messages (which can contain persisted personal data).
            throw RegistrationException.unavailable();
        }
    }
}
