package backend.vehiculos.service;

import org.springframework.stereotype.Component;

/**
 * Indica si el vehículo tiene TIVE documental (RF54).
 * En el MVP no existe tabla TIVE: siempre false. RF54 sustituirá esta consulta.
 */
@Component
public class PresenciaTiveVehiculo {

    public boolean existeParaVehiculo(long idVehiculo) {
        if (idVehiculo <= 0) {
            return false;
        }
        // RF54: SELECT EXISTS (SELECT 1 FROM mycar.tive WHERE id_vehiculo = ?)
        return false;
    }
}
