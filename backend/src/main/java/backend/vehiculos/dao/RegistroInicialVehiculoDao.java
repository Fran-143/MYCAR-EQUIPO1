package backend.vehiculos.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class RegistroInicialVehiculoDao {
    private final JdbcTemplate jdbc;

    public RegistroInicialVehiculoDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LecturaInicial registrar(long idVehiculo, int kilometraje, UUID idOperacion) {
        LecturaInicial lectura = Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO mycar.lectura_kilometraje (
                    id_vehiculo, valor_km, origen, id_operacion
                )
                VALUES (?, ?, 'INICIAL', ?)
                RETURNING id_lectura_km, fecha_lectura_utc
                """, (rs, fila) -> new LecturaInicial(
                        rs.getLong("id_lectura_km"),
                        rs.getObject("fecha_lectura_utc", OffsetDateTime.class).toInstant()
                ), idVehiculo, kilometraje, idOperacion));

        int insertadas = jdbc.update("""
                INSERT INTO mycar.config_recordatorio_km (
                    id_vehiculo, frecuencia, estado, fecha_base_utc
                )
                SELECT id_vehiculo, 'INTERDIARIO', 'PAUSADO', fecha_lectura_utc
                FROM mycar.lectura_kilometraje
                WHERE id_lectura_km = ? AND id_vehiculo = ?
                """, lectura.idLecturaKm(), idVehiculo);
        if (insertadas != 1) {
            throw new IllegalStateException("No se creó la configuración inicial de kilometraje");
        }
        return lectura;
    }

    public record LecturaInicial(long idLecturaKm, Instant fechaLectura) {
    }
}
