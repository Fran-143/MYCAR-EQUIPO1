package backend.vehiculos.dao;

import backend.vehiculos.dto.ContextoKilometrajeActual;
import backend.vehiculos.dto.LecturaKilometrajeHistorialDto;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
                rs.getObject("valor_km") == null ? BigDecimal.ZERO : rs.getBigDecimal("valor_km"),
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

    public long insertarLecturaActualizacion(long idVehiculo, BigDecimal kmNuevo, UUID idOperacion) {
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

    /** RF15: historial de lecturas del vehículo propio (ACTIVO o INACTIVO). Incluye última corrección RF16. */
    public List<LecturaKilometrajeHistorialDto> listarLecturasPropias(long idUsuario, long idVehiculo) {
        return jdbc.query("""
                SELECT l.id_lectura_km, l.valor_km, l.origen, l.fecha_lectura_utc,
                       c.km_anterior, c.motivo, c.fecha_rectificacion_utc
                FROM mycar.lectura_kilometraje l
                JOIN mycar.vehiculo v ON v.id_vehiculo = l.id_vehiculo
                LEFT JOIN LATERAL (
                    SELECT km_anterior, motivo, fecha_rectificacion_utc
                    FROM mycar.correccion_lectura_kilometraje
                    WHERE id_lectura_km = l.id_lectura_km
                    ORDER BY fecha_rectificacion_utc DESC, id_correccion DESC
                    LIMIT 1
                ) c ON TRUE
                WHERE v.id_usuario = ? AND v.id_vehiculo = ?
                  AND v.estado_vehiculo IN ('ACTIVO', 'INACTIVO')
                  AND l.origen IN ('INICIAL', 'ACTUALIZACION')
                ORDER BY l.fecha_lectura_utc DESC, l.id_lectura_km DESC
                """, (rs, row) -> {
            Timestamp ts = rs.getTimestamp("fecha_lectura_utc");
            Timestamp rect = rs.getTimestamp("fecha_rectificacion_utc");
            return new LecturaKilometrajeHistorialDto(
                    rs.getLong("id_lectura_km"),
                    rs.getBigDecimal("valor_km"),
                    rs.getString("origen"),
                    ts == null ? null : ts.toInstant().toString(),
                    rs.getBigDecimal("km_anterior"),
                    rs.getString("motivo"),
                    rect == null ? null : rect.toInstant().toString()
            );
        }, idUsuario, idVehiculo);
    }

    public boolean vehiculoPropioConsultable(long idUsuario, long idVehiculo) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.vehiculo
                WHERE id_usuario = ? AND id_vehiculo = ?
                  AND estado_vehiculo IN ('ACTIVO', 'INACTIVO')
                """, Integer.class, idUsuario, idVehiculo);
        return n != null && n > 0;
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
