package backend.vehiculos.dao;

import backend.vehiculos.dto.ContextoKilometrajeActual;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class KilometrajeVehiculoDao {
    private final JdbcTemplate jdbc;

    public KilometrajeVehiculoDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ContextoKilometrajeActual> consultarContextoPropio(long idUsuario, long idVehiculo) {
        return jdbc.query("""
                SELECT v.id_vehiculo, v.estado_vehiculo, v.version_km,
                       l.id_lectura_km, l.valor_km, l.fecha_lectura_utc
                FROM mycar.vehiculo v
                LEFT JOIN LATERAL (
                    SELECT id_lectura_km, valor_km, fecha_lectura_utc
                    FROM mycar.lectura_kilometraje
                    WHERE id_vehiculo = v.id_vehiculo AND origen IN ('INICIAL','ACTUALIZACION')
                    ORDER BY fecha_lectura_utc DESC, id_lectura_km DESC LIMIT 1
                ) l ON TRUE
                WHERE v.id_usuario = ? AND v.id_vehiculo = ?
                """, (rs, row) -> new ContextoKilometrajeActual(
                rs.getLong("id_vehiculo"),
                rs.getObject("id_lectura_km") == null ? 0L : rs.getLong("id_lectura_km"),
                rs.getObject("valor_km") == null ? 0 : rs.getInt("valor_km"),
                instant(rs.getTimestamp("fecha_lectura_utc")),
                rs.getInt("version_km"),
                rs.getString("estado_vehiculo")
        ), idUsuario, idVehiculo).stream().findFirst();
    }

    public Optional<VehiculoActivo> bloquearVehiculoPropio(long idUsuario, long idVehiculo) {
        return jdbc.query("""
                SELECT id_vehiculo, estado_vehiculo, version_km
                FROM mycar.vehiculo
                WHERE id_usuario = ? AND id_vehiculo = ?
                FOR UPDATE
                """, (rs, row) -> new VehiculoActivo(
                rs.getLong("id_vehiculo"), rs.getString("estado_vehiculo"), rs.getInt("version_km")
        ), idUsuario, idVehiculo).stream().findFirst();
    }

    public long insertarLecturaActualizacion(long idVehiculo, int kmNuevo, UUID idOperacion) {
        Long id = jdbc.queryForObject("""
                INSERT INTO mycar.lectura_kilometraje(id_vehiculo, valor_km, origen, id_operacion)
                VALUES (?, ?, 'ACTUALIZACION', ?)
                RETURNING id_lectura_km
                """, Long.class, idVehiculo, kmNuevo, idOperacion);
        return id == null ? 0L : id;
    }

    public Instant fechaLectura(long idLectura) {
        Timestamp ts = jdbc.queryForObject(
                "SELECT fecha_lectura_utc FROM mycar.lectura_kilometraje WHERE id_lectura_km = ?",
                Timestamp.class, idLectura);
        return ts == null ? null : ts.toInstant();
    }

    public boolean incrementarVersionSiCoincide(long idVehiculo, int versionBase) {
        return jdbc.update(
                "UPDATE mycar.vehiculo SET version_km = version_km + 1 WHERE id_vehiculo = ? AND version_km = ?",
                idVehiculo, versionBase) == 1;
    }

    private static Instant instant(Timestamp marca) {
        return marca == null ? null : marca.toInstant();
    }

    public record VehiculoActivo(long idVehiculo, String estadoVehiculo, int versionKm) {}
}
