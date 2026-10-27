package backend.vehiculos.dao;

import backend.vehiculos.dto.DatosRegistroVehiculo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Repository
@Transactional(readOnly = true)
public class VehiculoDao {

    private final JdbcTemplate jdbcTemplate;

    public VehiculoDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existePlaca(String placa) {
        Boolean existe = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM mycar.vehiculo
                    WHERE placa = ?
                )
                """,
                Boolean.class,
                placa
        );

        return Boolean.TRUE.equals(existe);
    }

    public boolean existeVin(String numeroVin) {
        if (numeroVin == null) {
            return false;
        }

        Boolean existe = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM mycar.vehiculo
                    WHERE numero_vin = ?
                )
                """,
                Boolean.class,
                numeroVin
        );

        return Boolean.TRUE.equals(existe);
    }

    public long contarRegistrados(long idUsuario) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM mycar.vehiculo
                WHERE id_usuario = ?
                """,
                Long.class,
                idUsuario
        );
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = false
    )
    public long insertarVehiculo(
            long idUsuario,
            DatosRegistroVehiculo datos,
            boolean principal
    ) {
        Objects.requireNonNull(datos, "datos");

        Long idVehiculo = jdbcTemplate.queryForObject(
                """
                INSERT INTO mycar.vehiculo (
                    id_usuario,
                    placa,
                    numero_vin,
                    marca,
                    modelo,
                    uso_vehiculo_codigo,
                    anio_fabricacion,
                    color,
                    id_tipo_motor,
                    tipo_motor_otro,
                    id_combustible,
                    combustible_otro,
                    id_tipo_aceite,
                    tipo_aceite_otro,
                    numero_serie,
                    numero_motor,
                    es_principal
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?
                )
                RETURNING id_vehiculo
                """,
                Long.class,
                idUsuario,
                datos.placa(),
                datos.numeroVin(),
                datos.marca(),
                datos.modelo(),
                datos.usoVehiculoCodigo(),
                datos.anioFabricacion(),
                datos.color(),
                datos.idTipoMotor(),
                datos.tipoMotorOtro(),
                datos.idCombustible(),
                datos.combustibleOtro(),
                datos.idTipoAceite(),
                datos.tipoAceiteOtro(),
                datos.numeroSerie(),
                datos.numeroMotor(),
                principal
        );

        return Objects.requireNonNull(
                idVehiculo,
                "PostgreSQL no devolvió el identificador del vehículo"
        );
    }
}