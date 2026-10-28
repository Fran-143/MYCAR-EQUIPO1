package backend.historial.dao;

import backend.historial.model.DatosHistorial.*;
import java.util.List;
import java.util.Optional;

public interface HistorialMantenimientoDao {
    List<Vehiculo> vehiculosPropios(long usuario);
    Optional<Vehiculo> vehiculoPropio(long usuario, long vehiculo);
    List<Mantenimiento> mantenimientosPropios(long usuario, long vehiculo);
}
