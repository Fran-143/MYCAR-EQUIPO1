package backend.mantenimiento.dao;

import backend.mantenimiento.model.SolicitudRegistroMantenimiento;

public interface RegistroMantenimientoDao {

    Long insertar(
            Long idUsuario,
            Long idTipoMantenimiento,
            SolicitudRegistroMantenimiento solicitud
    );
}