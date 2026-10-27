package backend.vehiculos.dao;

import backend.vehiculos.domain.ErrorRegistroVehiculo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lee el estado real existente. No autentica, crea cuentas ni asigna planes. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class CondicionesRegistroVehiculoDao {
    private final JdbcTemplate jdbc;

    public CondicionesRegistroVehiculoDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void exigirCuentaActiva(long idUsuario) {
        Boolean activa = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM mycar.usuario
                    WHERE id_usuario = ? AND estado_cuenta = 'ACTIVA'
                )
                """, Boolean.class, idUsuario);
        if (!Boolean.TRUE.equals(activa)) {
            throw new ErrorRegistroVehiculo("CUENTA_NO_AUTORIZADA");
        }
    }

    /** Solo para una operación nueva. Serializa altas de la misma cuenta. */
    public int bloquearYConsultarLimiteFree(long idUsuario) {
        var usuarios = jdbc.query("""
                SELECT id_usuario FROM mycar.usuario
                WHERE id_usuario = ? AND estado_cuenta = 'ACTIVA'
                FOR NO KEY UPDATE
                """, (rs, fila) -> rs.getLong(1), idUsuario);
        if (usuarios.isEmpty()) {
            throw new ErrorRegistroVehiculo("CUENTA_NO_AUTORIZADA");
        }

        var limites = jdbc.query("""
                SELECT p.max_vehiculos_registrados
                FROM mycar.suscripcion s
                JOIN mycar.plan p ON p.id_plan = s.id_plan
                WHERE s.id_usuario = ?
                  AND s.estado = 'ACTIVA'
                  AND s.inicio_utc <= clock_timestamp()
                  AND s.fin_utc IS NULL
                  AND p.codigo = 'FREE'
                  AND p.activo
                FOR SHARE OF s, p
                """, (rs, fila) -> rs.getInt(1), idUsuario);
        if (limites.size() != 1 || limites.getFirst() != 1) {
            throw new ErrorRegistroVehiculo("PLAN_FREE_NO_DISPONIBLE");
        }
        return limites.getFirst();
    }
}
