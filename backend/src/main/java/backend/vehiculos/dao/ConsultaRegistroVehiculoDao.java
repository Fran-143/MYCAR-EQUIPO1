package backend.vehiculos.dao;

import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.DatosCupoRegistro;
import backend.vehiculos.dto.DatosListadoVehiculos.VehiculoResumen;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class ConsultaRegistroVehiculoDao {
    private final JdbcTemplate jdbc;

    public ConsultaRegistroVehiculoDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DatosCupoRegistro consultarCupo(long idUsuario) {
        var resultados = jdbc.query("""
                SELECT p.max_vehiculos_registrados AS limite,
                       (SELECT count(*) FROM mycar.vehiculo v
                        WHERE v.id_usuario = s.id_usuario) AS registrados
                FROM mycar.suscripcion s
                JOIN mycar.plan p ON p.id_plan = s.id_plan
                WHERE s.id_usuario = ?
                  AND s.estado = 'ACTIVA'
                  AND s.inicio_utc <= clock_timestamp()
                  AND s.fin_utc IS NULL
                  AND p.codigo = 'FREE'
                  AND p.activo
                """, (rs, fila) -> new DatosCupoRegistro(
                        "FREE", rs.getInt("limite"), rs.getLong("registrados"),
                        rs.getLong("registrados") < rs.getInt("limite")
                ), idUsuario);
        if (resultados.size() != 1 || resultados.getFirst().limite() != 1) {
            throw new ErrorRegistroVehiculo("PLAN_FREE_NO_DISPONIBLE");
        }
        return resultados.getFirst();
    }

    public Optional<OperacionConsultada> consultarOperacion(UUID idOperacion, long idUsuario) {
        return jdbc.query("""
                SELECT estado, respuesta_json
                FROM mycar.operacion_idempotente
                WHERE id_operacion = ? AND id_usuario = ?
                  AND tipo_operacion = 'REGISTRAR_VEHICULO'
                """, (rs, fila) -> new OperacionConsultada(
                        rs.getString("estado"), rs.getString("respuesta_json")
                ), idOperacion, idUsuario).stream().findFirst();
    }

    /** Una lectura SQL obtiene solo filas propias, con su última lectura efectiva. No escribe. */
    public List<VehiculoResumen> listarPropios(long idUsuario) {
        return jdbc.query("""
                SELECT v.id_vehiculo, v.placa, v.marca, v.modelo, v.anio_fabricacion,
                       u.nombre AS uso, v.color,
                       CASE WHEN m.codigo='OTRO' THEN v.tipo_motor_otro ELSE m.nombre END AS motor,
                       CASE WHEN m.es_electrico THEN NULL
                            WHEN c.codigo='OTRO' THEN v.combustible_otro ELSE c.nombre END AS combustible,
                       v.estado_vehiculo, v.es_principal, km.valor_km
                FROM mycar.vehiculo v
                JOIN mycar.uso_vehiculo u ON u.codigo=v.uso_vehiculo_codigo
                JOIN mycar.tipo_motor m ON m.id_tipo_motor=v.id_tipo_motor
                LEFT JOIN mycar.combustible c ON c.id_combustible=v.id_combustible
                LEFT JOIN LATERAL (
                    SELECT l.valor_km FROM mycar.lectura_kilometraje l
                    WHERE l.id_vehiculo=v.id_vehiculo AND l.origen IN ('INICIAL','ACTUALIZACION')
                    ORDER BY l.fecha_lectura_utc DESC, l.id_lectura_km DESC LIMIT 1
                ) km ON TRUE
                WHERE v.id_usuario=?
                ORDER BY v.es_principal DESC, v.id_vehiculo DESC
                """, (rs, fila) -> new VehiculoResumen(
                    rs.getLong("id_vehiculo"), rs.getString("placa"), rs.getString("marca"),
                    rs.getString("modelo"), rs.getInt("anio_fabricacion"), rs.getString("uso"),
                    rs.getString("color"), rs.getString("motor"), rs.getString("combustible"),
                    rs.getString("estado_vehiculo"), rs.getBoolean("es_principal"),
                    rs.getObject("valor_km", Integer.class)
                ), idUsuario);
    }

    public record OperacionConsultada(String estado, String respuestaJson) {
    }
}
