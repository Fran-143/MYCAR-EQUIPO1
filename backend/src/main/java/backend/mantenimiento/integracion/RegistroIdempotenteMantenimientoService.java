package backend.mantenimiento.integracion;

import backend.mantenimiento.model.EstadoOperacionRegistro;
import backend.mantenimiento.model.ResultadoRegistroMantenimiento;
import backend.mantenimiento.model.SolicitudRegistroMantenimiento;
import backend.mantenimiento.service.RegistroMantenimientoService;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Adaptador RF17: el servicio existente controla la transacción completa. */
@Service
public class RegistroIdempotenteMantenimientoService implements RegistroIdempotenteMantenimiento {
    private final RegistroMantenimientoService servicio;

    public RegistroIdempotenteMantenimientoService(RegistroMantenimientoService servicio) {
        this.servicio = servicio;
    }

    @Override
    public ResultadoRegistroMantenimiento registrar(long usuario, SolicitudRegistroMantenimiento solicitud) {
        return servicio.registrarResultado(usuario, solicitud);
    }

    @Override
    public EstadoOperacionRegistro consultar(long usuario, UUID id) {
        return servicio.consultarOperacion(usuario, id);
    }
}
