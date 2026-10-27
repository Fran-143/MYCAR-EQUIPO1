package backend.mantenimiento.dao;

import backend.mantenimiento.model.TipoMantenimiento;

import java.util.Optional;

public interface TipoMantenimientoDao {

    Optional<TipoMantenimiento> buscarPorCodigo(String codigo);
}