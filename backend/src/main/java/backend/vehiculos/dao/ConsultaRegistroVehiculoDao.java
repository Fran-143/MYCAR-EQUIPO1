package backend.vehiculos.dao;

import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.DatosCupoRegistro;
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

    public record OperacionConsultada(String estado, String respuestaJson) {
    }
}
