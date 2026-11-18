package backend.vehiculos.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Indica si el vehículo tiene TIVE documental (RF52/53/54 entrega TIVE).
 */
@Component
public class PresenciaTiveVehiculo {
    private final JdbcTemplate jdbc;

    public PresenciaTiveVehiculo(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existeParaVehiculo(long idVehiculo) {
        if (idVehiculo <= 0) {
            return false;
        }
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*)::int FROM mycar.tive WHERE id_vehiculo = ?",
                Integer.class, idVehiculo);
        return n != null && n > 0;
    }
}
