package backend.mantenimiento.dao;

import backend.mantenimiento.model.VehiculoMantenimiento;

import java.util.Optional;

public interface VehiculoMantenimientoDao {

    Optional<VehiculoMantenimiento> buscarPorId(Long idVehiculo);

    Optional<VehiculoMantenimiento> buscarPorIdParaRegistro(Long idVehiculo);
}
