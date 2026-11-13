package backend.vehiculos.dao;

import backend.vehiculos.dto.DatosActualizacionVehiculo;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

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

    /** Unicidad global de VIN excluyendo el propio vehículo (RF11). */
    public boolean existeVinEnOtro(String numeroVin, long idVehiculo) {
        if (numeroVin == null) {
            return false;
        }
        Boolean existe = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM mycar.vehiculo
                    WHERE numero_vin = ?
                      AND id_vehiculo <> ?
                )
                """,
                Boolean.class,
                numeroVin,
                idVehiculo
        );
        return Boolean.TRUE.equals(existe);
    }

    public Optional<VehiculoEditable> obtenerEditablePropio(long idUsuario, long idVehiculo) {
        return jdbcTemplate.query(
                """
                SELECT id_vehiculo, placa, estado_vehiculo, marca, modelo, uso_vehiculo_codigo,
                       anio_fabricacion, color, id_tipo_motor, tipo_motor_otro,
                       id_combustible, combustible_otro, id_tipo_aceite, tipo_aceite_otro,
                       numero_vin, numero_serie, numero_motor
                FROM mycar.vehiculo
                WHERE id_usuario = ? AND id_vehiculo = ?
                """,
                (rs, fila) -> new VehiculoEditable(
                        rs.getLong("id_vehiculo"),
                        rs.getString("placa"),
                        rs.getString("estado_vehiculo"),
                        rs.getString("marca"),
                        rs.getString("modelo"),
                        rs.getString("uso_vehiculo_codigo"),
                        rs.getInt("anio_fabricacion"),
                        rs.getString("color"),
                        rs.getObject("id_tipo_motor", Long.class),
                        rs.getString("tipo_motor_otro"),
                        rs.getObject("id_combustible", Long.class),
                        rs.getString("combustible_otro"),
                        rs.getObject("id_tipo_aceite", Long.class),
                        rs.getString("tipo_aceite_otro"),
                        rs.getString("numero_vin"),
                        rs.getString("numero_serie"),
                        rs.getString("numero_motor")
                ),
                idUsuario,
                idVehiculo
        ).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = false)
    public void actualizarDatosGenerales(long idUsuario, long idVehiculo, DatosActualizacionVehiculo datos) {
        Objects.requireNonNull(datos, "datos");
        int filas = jdbcTemplate.update(
                """
                UPDATE mycar.vehiculo
                SET marca = ?,
                    modelo = ?,
                    uso_vehiculo_codigo = ?,
                    anio_fabricacion = ?,
                    color = ?,
                    id_tipo_motor = ?,
                    tipo_motor_otro = ?,
                    id_combustible = ?,
                    combustible_otro = ?,
                    id_tipo_aceite = ?,
                    tipo_aceite_otro = ?,
                    numero_vin = ?,
                    numero_serie = ?,
                    numero_motor = ?
                WHERE id_usuario = ?
                  AND id_vehiculo = ?
                  AND estado_vehiculo = 'ACTIVO'
                """,
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
                datos.numeroVin(),
                datos.numeroSerie(),
                datos.numeroMotor(),
                idUsuario,
                idVehiculo
        );
        if (filas != 1) {
            throw new IllegalStateException("No se actualizó el vehículo editable");
        }
    }

    public record VehiculoEditable(
            long idVehiculo,
            String placa,
            String estadoVehiculo,
            String marca,
            String modelo,
            String usoVehiculoCodigo,
            int anioFabricacion,
            String color,
            Long idTipoMotor,
            String tipoMotorOtro,
            Long idCombustible,
            String combustibleOtro,
            Long idTipoAceite,
            String tipoAceiteOtro,
            String numeroVin,
            String numeroSerie,
            String numeroMotor
    ) {
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

    /**
     * Evita un segundo alta del mismo usuario con exactamente los mismos datos generales
     * (marca, modelo, uso, año, color, motor e identificadores). La placa se valida aparte.
     */
    public boolean existeVehiculoIdentico(long idUsuario, DatosRegistroVehiculo datos) {
        return existeVehiculoIdentico(
                idUsuario,
                null,
                datos.marca(),
                datos.modelo(),
                datos.usoVehiculoCodigo(),
                datos.anioFabricacion(),
                datos.color(),
                datos.idTipoMotor(),
                datos.idCombustible(),
                datos.idTipoAceite(),
                datos.tipoMotorOtro(),
                datos.combustibleOtro(),
                datos.tipoAceiteOtro(),
                datos.numeroVin(),
                datos.numeroSerie(),
                datos.numeroMotor()
        );
    }

    /** RF11: mismo criterio, excluyendo el vehículo que se está editando. */
    public boolean existeVehiculoIdenticoEnOtro(
            long idUsuario, long idVehiculoExcluido, DatosActualizacionVehiculo datos
    ) {
        Objects.requireNonNull(datos, "datos");
        return existeVehiculoIdentico(
                idUsuario,
                idVehiculoExcluido,
                datos.marca(),
                datos.modelo(),
                datos.usoVehiculoCodigo(),
                datos.anioFabricacion(),
                datos.color(),
                datos.idTipoMotor(),
                datos.idCombustible(),
                datos.idTipoAceite(),
                datos.tipoMotorOtro(),
                datos.combustibleOtro(),
                datos.tipoAceiteOtro(),
                datos.numeroVin(),
                datos.numeroSerie(),
                datos.numeroMotor()
        );
    }

    private boolean existeVehiculoIdentico(
            long idUsuario,
            Long idVehiculoExcluido,
            String marca,
            String modelo,
            String usoVehiculoCodigo,
            Integer anioFabricacion,
            String color,
            Long idTipoMotor,
            Long idCombustible,
            Long idTipoAceite,
            String tipoMotorOtro,
            String combustibleOtro,
            String tipoAceiteOtro,
            String numeroVin,
            String numeroSerie,
            String numeroMotor
    ) {
        Boolean existe = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM mycar.vehiculo
                    WHERE id_usuario = ?
                      AND (?::bigint IS NULL OR id_vehiculo <> ?)
                      AND marca = ?
                      AND modelo = ?
                      AND uso_vehiculo_codigo = ?
                      AND anio_fabricacion = ?
                      AND color = ?
                      AND id_tipo_motor = ?
                      AND id_combustible IS NOT DISTINCT FROM ?
                      AND id_tipo_aceite IS NOT DISTINCT FROM ?
                      AND tipo_motor_otro IS NOT DISTINCT FROM ?
                      AND combustible_otro IS NOT DISTINCT FROM ?
                      AND tipo_aceite_otro IS NOT DISTINCT FROM ?
                      AND numero_vin IS NOT DISTINCT FROM ?
                      AND numero_serie IS NOT DISTINCT FROM ?
                      AND numero_motor IS NOT DISTINCT FROM ?
                )
                """,
                Boolean.class,
                idUsuario,
                idVehiculoExcluido,
                idVehiculoExcluido,
                marca,
                modelo,
                usoVehiculoCodigo,
                anioFabricacion,
                color,
                idTipoMotor,
                idCombustible,
                idTipoAceite,
                tipoMotorOtro,
                combustibleOtro,
                tipoAceiteOtro,
                numeroVin,
                numeroSerie,
                numeroMotor
        );
        return Boolean.TRUE.equals(existe);
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

    public record VehiculoEstadoOperativo(
            long idVehiculo,
            String estadoVehiculo,
            boolean esPrincipal,
            String placa,
            String marca,
            String modelo
    ) {
    }

    /** Serializa cambios de estado/principal de la misma cuenta. */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = false)
    public void bloquearVehiculosDelUsuario(long idUsuario) {
        jdbcTemplate.query(
                """
                SELECT id_vehiculo FROM mycar.vehiculo
                WHERE id_usuario = ?
                ORDER BY id_vehiculo
                FOR UPDATE
                """,
                (rs, fila) -> rs.getLong(1),
                idUsuario
        );
    }

    public Optional<VehiculoEstadoOperativo> obtenerEstadoOperativoPropio(long idUsuario, long idVehiculo) {
        return jdbcTemplate.query(
                """
                SELECT id_vehiculo, estado_vehiculo, es_principal, placa, marca, modelo
                FROM mycar.vehiculo
                WHERE id_usuario = ? AND id_vehiculo = ?
                """,
                (rs, fila) -> new VehiculoEstadoOperativo(
                        rs.getLong("id_vehiculo"),
                        rs.getString("estado_vehiculo"),
                        rs.getBoolean("es_principal"),
                        rs.getString("placa"),
                        rs.getString("marca"),
                        rs.getString("modelo")
                ),
                idUsuario,
                idVehiculo
        ).stream().findFirst();
    }

    public long contarActivos(long idUsuario) {
        Long n = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM mycar.vehiculo
                WHERE id_usuario = ? AND estado_vehiculo = 'ACTIVO'
                """,
                Long.class,
                idUsuario
        );
        return n == null ? 0L : n;
    }

    /**
     * FREE: máximo 1 ACTIVO (regla RF12/C04). PREMIUM u otros: max_vehiculos_registrados del plan.
     */
    public int limiteActivosPlan(long idUsuario) {
        var filas = jdbcTemplate.query(
                """
                SELECT p.codigo, p.max_vehiculos_registrados
                FROM mycar.suscripcion s
                JOIN mycar.plan p ON p.id_plan = s.id_plan
                WHERE s.id_usuario = ?
                  AND s.estado = 'ACTIVA'
                  AND s.inicio_utc <= clock_timestamp()
                  AND s.fin_utc IS NULL
                  AND p.codigo IN ('FREE', 'PREMIUM')
                  AND p.activo
                """,
                (rs, fila) -> new Object[]{rs.getString(1), rs.getInt(2)},
                idUsuario
        );
        if (filas.size() != 1) {
            throw new backend.vehiculos.domain.ErrorRegistroVehiculo("PLAN_FREE_NO_DISPONIBLE");
        }
        String codigo = (String) filas.getFirst()[0];
        int maxPlan = (Integer) filas.getFirst()[1];
        if ("FREE".equals(codigo)) {
            return 1;
        }
        return Math.max(1, maxPlan);
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = false)
    public void transferirPrincipal(long idUsuario, long idNuevoPrincipal) {
        jdbcTemplate.update(
                """
                UPDATE mycar.vehiculo
                SET es_principal = FALSE
                WHERE id_usuario = ? AND es_principal = TRUE
                """,
                idUsuario
        );
        int filas = jdbcTemplate.update(
                """
                UPDATE mycar.vehiculo
                SET es_principal = TRUE
                WHERE id_usuario = ?
                  AND id_vehiculo = ?
                  AND estado_vehiculo = 'ACTIVO'
                """,
                idUsuario,
                idNuevoPrincipal
        );
        if (filas != 1) {
            throw new IllegalStateException("No se pudo asignar el vehículo principal");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = false)
    public void actualizarEstado(long idUsuario, long idVehiculo, String estado) {
        int filas = jdbcTemplate.update(
                """
                UPDATE mycar.vehiculo
                SET estado_vehiculo = ?,
                    es_principal = CASE WHEN ? = 'INACTIVO' THEN FALSE ELSE es_principal END
                WHERE id_usuario = ? AND id_vehiculo = ?
                """,
                estado,
                estado,
                idUsuario,
                idVehiculo
        );
        if (filas != 1) {
            throw new IllegalStateException("No se actualizó el estado del vehículo");
        }
    }
}