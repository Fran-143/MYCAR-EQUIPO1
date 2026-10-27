package backend.mantenimiento.dao;

import backend.mantenimiento.model.LecturaKmReferencia;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class LecturaKmMantenimientoJdbcDao
        implements LecturaKmMantenimientoDao {

    private final JdbcTemplate jdbcTemplate;

    public LecturaKmMantenimientoJdbcDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<LecturaKmReferencia> buscarPorId(Long idLecturaKm) {
        String sql = """
                SELECT id_lectura_km,
                       id_vehiculo,
                       valor_km,
                       fecha_lectura_utc
                FROM mycar.lectura_kilometraje
                WHERE id_lectura_km = ?
                """;

        var lecturas = jdbcTemplate.query(
                sql,
                this::mapearLectura,
                idLecturaKm
        );

        return lecturas.stream().findFirst();
    }

    @Override
    public Optional<LecturaKmReferencia> buscarPrimeraLecturaPosterior(
            Long idVehiculo, LocalDate fechaServicio) {
        var lecturas = jdbcTemplate.query(
                """
                SELECT id_lectura_km, id_vehiculo, valor_km, fecha_lectura_utc
                FROM mycar.lectura_kilometraje
                WHERE id_vehiculo = ?
                  AND origen IN ('INICIAL', 'ACTUALIZACION')
                  AND (fecha_lectura_utc AT TIME ZONE 'America/Lima')::date > ?
                ORDER BY fecha_lectura_utc ASC, id_lectura_km ASC
                LIMIT 1
                """,
                this::mapearLectura,
                idVehiculo,
                fechaServicio
        );
        return lecturas.stream().findFirst();
    }

    @Override
    public Optional<LecturaKmReferencia> buscarUltimaLecturaAnterior(
            Long idVehiculo, LocalDate fechaServicio) {
        var lecturas = jdbcTemplate.query(
                """
                SELECT id_lectura_km, id_vehiculo, valor_km, fecha_lectura_utc
                FROM mycar.lectura_kilometraje
                WHERE id_vehiculo = ?
                  AND origen IN ('INICIAL', 'ACTUALIZACION')
                  AND (fecha_lectura_utc AT TIME ZONE 'America/Lima')::date < ?
                ORDER BY fecha_lectura_utc DESC, id_lectura_km DESC
                LIMIT 1
                """,
                this::mapearLectura,
                idVehiculo,
                fechaServicio
        );
        return lecturas.stream().findFirst();
    }

    private LecturaKmReferencia mapearLectura(ResultSet rs, int fila)
            throws SQLException {
        return new LecturaKmReferencia(
                rs.getLong("id_lectura_km"),
                rs.getLong("id_vehiculo"),
                rs.getInt("valor_km"),
                rs.getObject("fecha_lectura_utc", OffsetDateTime.class)
                        .toInstant()
        );
    }
}
