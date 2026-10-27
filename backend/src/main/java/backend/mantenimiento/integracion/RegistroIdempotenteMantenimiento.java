package backend.mantenimiento.integracion;

import backend.mantenimiento.model.EstadoOperacionRegistro;
import backend.mantenimiento.model.ResultadoRegistroMantenimiento;
import backend.mantenimiento.model.SolicitudRegistroMantenimiento;
import java.util.UUID;

/**
 * Registro y consulta de operaciones RF17 autorizadas por usuario y tipo.
 * La implementación usa operacion_idempotente y confirma el snapshot junto con
 * el mantenimiento y sus repuestos en una sola transacción.
 */
public interface RegistroIdempotenteMantenimiento {
    ResultadoRegistroMantenimiento registrar(
            long idUsuarioAutenticado, SolicitudRegistroMantenimiento solicitud);
    EstadoOperacionRegistro consultar(long idUsuarioAutenticado, UUID idOperacion);
}
