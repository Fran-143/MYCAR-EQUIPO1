package backend.alertas.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class OperacionAlertaJdbcDao {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OperacionAlertaJdbcDao(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public boolean intentarCrear(UUID id, long usuario, String huella) {
        exigirTx();
        return jdbc.update("""
                INSERT INTO mycar.operacion_idempotente
                    (id_operacion, id_usuario, tipo_operacion, hash_solicitud)
                VALUES (?, ?, 'PROGRAMAR_ALERTA', ?)
                ON CONFLICT (id_operacion) DO NOTHING
                """, id, usuario, huella) == 1;
    }

    public Optional<OperacionAlerta> buscar(UUID id) {
        return jdbc.query("""
                SELECT id_operacion, id_usuario, hash_solicitud, estado, respuesta_json::text
                FROM mycar.operacion_idempotente
                WHERE id_operacion = ? AND tipo_operacion = 'PROGRAMAR_ALERTA'
                """, (rs, n) -> new OperacionAlerta(
                rs.getObject("id_operacion", UUID.class),
                rs.getLong("id_usuario"),
                rs.getString("hash_solicitud"),
                rs.getString("estado"),
                rs.getString("respuesta_json")), id).stream().findFirst();
    }

    public Optional<OperacionAlerta> buscarYBloquear(UUID id) {
        exigirTx();
        return jdbc.query("""
                SELECT id_operacion, id_usuario, hash_solicitud, estado, respuesta_json::text
                FROM mycar.operacion_idempotente
                WHERE id_operacion = ? AND tipo_operacion = 'PROGRAMAR_ALERTA'
                FOR UPDATE
                """, (rs, n) -> new OperacionAlerta(
                rs.getObject("id_operacion", UUID.class),
                rs.getLong("id_usuario"),
                rs.getString("hash_solicitud"),
                rs.getString("estado"),
                rs.getString("respuesta_json")), id).stream().findFirst();
    }

    public void confirmar(UUID id, long usuario, long idAlerta, String estado) {
        exigirTx();
        String payload;
        try {
            payload = json.writeValueAsString(java.util.Map.of(
                    "idAlerta", idAlerta,
                    "estado", estado));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        int filas = jdbc.update("""
                UPDATE mycar.operacion_idempotente
                SET estado = 'CONFIRMADA', respuesta_json = CAST(? AS jsonb)
                WHERE id_operacion = ? AND id_usuario = ? AND estado = 'EN_PROCESO'
                """, payload, id, usuario);
        if (filas != 1) throw new IllegalStateException("No se pudo confirmar PROGRAMAR_ALERTA");
    }

    private void exigirTx() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("PROGRAMAR_ALERTA requiere transacción activa");
        }
    }

    public record OperacionAlerta(UUID idOperacion, long idUsuario, String hashSolicitud, String estado, String respuestaJson) { }
}
