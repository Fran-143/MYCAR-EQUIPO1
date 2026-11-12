package backend.perfil.persistence;

import backend.perfil.ProfileData;
import backend.perfil.ProfileStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public class JdbcProfileStore implements ProfileStore {
    private final JdbcTemplate jdbc;
    private static final RowMapper<ProfileData> PROFILE = (r, n) -> new ProfileData(
            r.getString("nombre"),
            r.getString("apellido"),
            r.getString("correo"),
            r.getObject("fecha_nacimiento", LocalDate.class));
    private static final RowMapper<Credential> CREDENTIAL = (r, n) -> new Credential(
            r.getLong("id_usuario"),
            r.getString("password_hash"),
            r.getString("estado_cuenta"));

    public JdbcProfileStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<ProfileData> findActiveProfile(long userId) {
        return jdbc.query("""
                SELECT nombre, apellido, correo, fecha_nacimiento
                  FROM mycar.usuario
                 WHERE id_usuario=? AND estado_cuenta='ACTIVA'
                """, PROFILE, userId).stream().findFirst();
    }

    @Override
    public Optional<Credential> lockCredential(long userId) {
        return jdbc.query("""
                SELECT id_usuario, password_hash, estado_cuenta
                  FROM mycar.usuario
                 WHERE id_usuario=?
                 FOR NO KEY UPDATE
                """, CREDENTIAL, userId).stream().findFirst();
    }

    @Override
    public int updatePersonalData(long userId, String nombre, String apellido, LocalDate fechaNacimiento) {
        return jdbc.update("""
                UPDATE mycar.usuario
                   SET nombre=?, apellido=?, fecha_nacimiento=?
                 WHERE id_usuario=? AND estado_cuenta='ACTIVA'
                """, nombre, apellido, fechaNacimiento, userId);
    }

    @Override
    public int updatePasswordHash(long userId, String passwordHash) {
        return jdbc.update("""
                UPDATE mycar.usuario
                   SET password_hash=?
                 WHERE id_usuario=? AND estado_cuenta='ACTIVA'
                """, passwordHash, userId);
    }
}
