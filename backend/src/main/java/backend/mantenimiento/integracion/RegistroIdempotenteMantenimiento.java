package backend.mantenimiento.integracion;

import backend.mantenimiento.model.EstadoOperacionRegistro;
import backend.mantenimiento.model.ResultadoRegistroMantenimiento;
import backend.mantenimiento.model.SolicitudRegistroMantenimiento;
import java.util.UUID;

/**
 * PUERTO PENDIENTE DE CONTRATO COMPARTIDO. No se suministra un bean por defecto.
 * El adaptador del equipo debe reservar idOperacion, comprobar usuario/tipo/huella,
 * invocar RegistroMantenimientoService y conservar el resultado en UNA transacción.
 * En reintentos debe devolver el snapshot confirmado sin volver a insertar.
 * consultar debe autorizar usuario/tipo; nunca consultar por UUID sin autorización.
 * No implementar un protocolo alternativo hasta confirmar el acuerdo del equipo.
 */
public interface RegistroIdempotenteMantenimiento {
    ResultadoRegistroMantenimiento registrar(
            long idUsuarioAutenticado, SolicitudRegistroMantenimiento solicitud);
    EstadoOperacionRegistro consultar(long idUsuarioAutenticado, UUID idOperacion);
}
