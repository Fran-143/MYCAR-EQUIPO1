package backend.push.dao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DispositivoPushJdbcDao {
    private final JdbcTemplate jdbc;

    public DispositivoPushJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(long usuario, UUID instalacion, String proveedor, String token) {
        jdbc.update("""
                INSERT INTO mycar.dispositivo_push (
                    id_usuario, id_instalacion, proveedor, token, activo, creado_utc, actualizado_utc)
                VALUES (?, ?, ?, ?, TRUE, clock_timestamp(), clock_timestamp())
                ON CONFLICT (id_instalacion) DO UPDATE SET
                    id_usuario = EXCLUDED.id_usuario,
                    proveedor = EXCLUDED.proveedor,
                    token = EXCLUDED.token,
                    activo = TRUE,
                    actualizado_utc = clock_timestamp()
                """, usuario, instalacion, proveedor, token);
        // Un token no debe quedar activo en otra instalación.
        jdbc.update("""
                UPDATE mycar.dispositivo_push
                SET activo = FALSE, actualizado_utc = clock_timestamp()
                WHERE proveedor = ? AND token = ? AND id_instalacion <> ?
                """, proveedor, token, instalacion);
    }

    public int desactivarPorInstalacion(long usuario, UUID instalacion) {
        return jdbc.update("""
                UPDATE mycar.dispositivo_push
                SET activo = FALSE, actualizado_utc = clock_timestamp()
                WHERE id_usuario = ? AND id_instalacion = ? AND activo
                """, usuario, instalacion);
    }

    public int desactivarPorToken(String proveedor, String token) {
        return jdbc.update("""
                UPDATE mycar.dispositivo_push
                SET activo = FALSE, actualizado_utc = clock_timestamp()
                WHERE proveedor = ? AND token = ? AND activo
                """, proveedor, token);
    }

    public List<TokenActivo> listarActivos(long usuario, String proveedor) {
        return jdbc.query("""
                SELECT token, id_instalacion
                FROM mycar.dispositivo_push
                WHERE id_usuario = ? AND proveedor = ? AND activo
                """, (rs, i) -> new TokenActivo(
                rs.getString("token"),
                (UUID) rs.getObject("id_instalacion")), usuario, proveedor);
    }

    public Optional<Boolean> existeActivo(long usuario, UUID instalacion) {
        Boolean ok = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM mycar.dispositivo_push
                    WHERE id_usuario = ? AND id_instalacion = ? AND activo)
                """, Boolean.class, usuario, instalacion);
        return Optional.ofNullable(ok);
    }

    public record TokenActivo(String token, UUID idInstalacion) {
    }
}
