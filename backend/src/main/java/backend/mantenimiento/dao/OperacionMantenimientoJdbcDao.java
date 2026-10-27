package backend.mantenimiento.dao;

import backend.mantenimiento.model.OperacionMantenimiento;
import backend.mantenimiento.model.ResultadoRegistroMantenimiento;
import backend.mantenimiento.integracion.JsonOperacionMantenimiento;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OperacionMantenimientoJdbcDao implements OperacionMantenimientoDao {
    private final JdbcTemplate jdbc;

    public OperacionMantenimientoJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean intentarCrear(UUID id, Long usuario, String huella) {
        exigirTransaccion();
        // La PK también serializa dos peticiones simultáneas con el mismo UUID.
        return jdbc.update("""
                INSERT INTO mycar.operacion_idempotente
                    (id_operacion, id_usuario, tipo_operacion, hash_solicitud)
                VALUES (?, ?, 'REGISTRAR_MANTENIMIENTO', ?)
                ON CONFLICT (id_operacion) DO NOTHING
                """, id, usuario, huella) == 1;
    }

    @Override
    public Optional<OperacionMantenimiento> buscarYBloquear(UUID id) {
        exigirTransaccion();
        return jdbc.query("""
                SELECT id_operacion, id_usuario, tipo_operacion, hash_solicitud,
                       estado, respuesta_json::text AS respuesta
                FROM mycar.operacion_idempotente
                WHERE id_operacion = ? FOR UPDATE
                """, (rs, fila) -> new OperacionMantenimiento(
                rs.getObject("id_operacion", UUID.class), rs.getLong("id_usuario"),
                rs.getString("tipo_operacion"), rs.getString("hash_solicitud"),
                rs.getString("estado"), rs.getString("respuesta")), id).stream().findFirst();
    }

    @Override
    public void confirmar(UUID id, Long usuario, ResultadoRegistroMantenimiento resultado) {
        exigirTransaccion();
        int filas = jdbc.update("""
                UPDATE mycar.operacion_idempotente
                SET estado = 'CONFIRMADA', respuesta_json = CAST(? AS jsonb)
                WHERE id_operacion = ? AND id_usuario = ?
                  AND tipo_operacion = 'REGISTRAR_MANTENIMIENTO' AND estado = 'EN_PROCESO'
                """, JsonOperacionMantenimiento.escribir(resultado), id, usuario);
        if (filas != 1) {
            throw new IllegalStateException("No se pudo confirmar la operación RF17");
        }
    }

    private void exigirTransaccion() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La operación RF17 requiere una transacción activa");
        }
    }
}
