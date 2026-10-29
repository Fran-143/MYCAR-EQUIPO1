package backend.mantenimiento.service;

import backend.mantenimiento.dao.VehiculoMantenimientoDao;
import backend.mantenimiento.model.VehiculoMantenimiento;
import backend.mantenimiento.model.MantenimientoNoDisponibleException;

import java.util.Objects;

public class VehiculoMantenimientoService {

    private final VehiculoMantenimientoDao dao;

    public VehiculoMantenimientoService(
            VehiculoMantenimientoDao dao
    ) {
        this.dao = dao;
    }

    public VehiculoMantenimiento obtenerVehiculoPropioActivo(
            Long idUsuario,
            Long idVehiculo
    ) {
        VehiculoMantenimiento vehiculo = dao.buscarPorId(idVehiculo)
                .orElseThrow(() -> new MantenimientoNoDisponibleException());

        return verificarPropiedadYEstado(idUsuario, vehiculo);
    }

    public VehiculoMantenimiento obtenerVehiculoPropioActivoParaRegistro(
            Long idUsuario,
            Long idVehiculo
    ) {
        VehiculoMantenimiento vehiculo = dao.buscarPorIdParaRegistro(idVehiculo)
                .orElseThrow(() -> new MantenimientoNoDisponibleException());

        return verificarPropiedadYEstado(idUsuario, vehiculo);
    }

    private VehiculoMantenimiento verificarPropiedadYEstado(
            Long idUsuario, VehiculoMantenimiento vehiculo) {
        if (!Objects.equals(vehiculo.idUsuario(), idUsuario)) {
            throw new MantenimientoNoDisponibleException();
        }

        if (!"ACTIVO".equals(vehiculo.estadoVehiculo())) {
            throw new MantenimientoNoDisponibleException();
        }

        return vehiculo;
    }
}
