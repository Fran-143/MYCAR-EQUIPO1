package backend.mantenimiento.dao;

import backend.mantenimiento.model.VehiculoMantenimiento;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

@Repository
public class VehiculoMantenimientoJdbcDao
        implements VehiculoMantenimientoDao {

    private final JdbcTemplate jdbcTemplate;

    public VehiculoMantenimientoJdbcDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<VehiculoMantenimiento> buscarPorId(Long idVehiculo) {
        String sql = """
                SELECT id_vehiculo,
                       id_usuario,
                       estado_vehiculo
                FROM mycar.vehiculo
                WHERE id_vehiculo = ?
                """;

        var vehiculos = jdbcTemplate.query(
                sql,
                (rs, fila) -> new VehiculoMantenimiento(
                        rs.getLong("id_vehiculo"),
                        rs.getLong("id_usuario"),
                        rs.getString("estado_vehiculo")
                ),
                idVehiculo
        );

        return vehiculos.stream().findFirst();
    }

    @Override
    public Optional<VehiculoMantenimiento> buscarPorIdParaRegistro(
            Long idVehiculo) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "La lectura bloqueada del vehículo requiere una transacción activa"
            );
        }
        var vehiculos = jdbcTemplate.query(
                """
                SELECT id_vehiculo, id_usuario, estado_vehiculo
                FROM mycar.vehiculo
                WHERE id_vehiculo = ?
                FOR UPDATE
                """,
                (rs, fila) -> new VehiculoMantenimiento(
                        rs.getLong("id_vehiculo"),
                        rs.getLong("id_usuario"),
                        rs.getString("estado_vehiculo")
                ),
                idVehiculo
        );
        return vehiculos.stream().findFirst();
    }
}
