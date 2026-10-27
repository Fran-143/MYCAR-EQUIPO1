package backend.vehiculos.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class OperacionVehiculoDao {

    private final JdbcTemplate jdbcTemplate;

    public OperacionVehiculoDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean reservar(
            UUID idOperacion,
            long idUsuario,
            String tipoOperacion,
            String hashSolicitud
    ) {
        int filasInsertadas = jdbcTemplate.update(
                """
                INSERT INTO mycar.operacion_idempotente (
                    id_operacion,
                    id_usuario,
                    tipo_operacion,
                    hash_solicitud
                )
                VALUES (?, ?, ?, ?)
                ON CONFLICT (id_operacion) DO NOTHING
                """,
                idOperacion,
                idUsuario,
                tipoOperacion,
                hashSolicitud
        );

        return filasInsertadas == 1;
    }

    public Optional<DatosOperacion> buscarParaActualizar(
            UUID idOperacion,
            long idUsuario,
            String tipoOperacion
    ) {
        var resultados = jdbcTemplate.query(
                """
                SELECT hash_solicitud, estado, respuesta_json
                FROM mycar.operacion_idempotente
                WHERE id_operacion = ?
                  AND id_usuario = ?
                  AND tipo_operacion = ?
                FOR UPDATE
                """,
                (rs, numeroFila) -> new DatosOperacion(
                        rs.getString("hash_solicitud"),
                        rs.getString("estado"),
                        rs.getString("respuesta_json")
                ),
                idOperacion,
                idUsuario,
                tipoOperacion
        );

        return resultados.stream().findFirst();
    }

    public boolean confirmar(
            UUID idOperacion,
            long idUsuario,
            String tipoOperacion,
            String hashSolicitud,
            String respuestaJson
    ) {
        Objects.requireNonNull(
                respuestaJson,
                "La respuesta de la operación es obligatoria"
        );

        int filasActualizadas = jdbcTemplate.update(
                """
                UPDATE mycar.operacion_idempotente
                SET estado = 'CONFIRMADA',
                    respuesta_json = CAST(? AS jsonb)
                WHERE id_operacion = ?
                  AND id_usuario = ?
                  AND tipo_operacion = ?
                  AND hash_solicitud = ?
                  AND estado = 'EN_PROCESO'
                """,
                respuestaJson,
                idOperacion,
                idUsuario,
                tipoOperacion,
                hashSolicitud
        );

        return filasActualizadas == 1;
    }

    public record DatosOperacion(
            String hashSolicitud,
            String estado,
            String respuestaJson
    ) {
    }
}