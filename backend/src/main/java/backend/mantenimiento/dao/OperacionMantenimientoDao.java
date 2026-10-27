package backend.mantenimiento.dao;

import backend.mantenimiento.model.OperacionMantenimiento;

import java.util.Optional;
import java.util.UUID;

public interface OperacionMantenimientoDao {

    boolean intentarCrear(
            UUID idOperacion,
            Long idUsuario,
            String hashSolicitud
    );

    Optional<OperacionMantenimiento> buscarYBloquear(
            UUID idOperacion
    );

    void confirmar(
            UUID idOperacion,
            Long idUsuario,
            Long idMantenimiento
    );
}