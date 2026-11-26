package backend.vehiculos.service;

import backend.vehiculos.domain.ErrorRegistroVehiculo;
import org.springframework.stereotype.Component;

/**
 * EA2: punto único para denegar compartición por restricciones distintas de INACTIVO.
 * Hoy no hay columna de dominio; siempre permite. INACTIVO no es motivo de denegación.
 */
@Component
public class CompartirVehiculoGate {

    public void exigirPermitido(long idUsuarioAutenticado, long idVehiculo, String estadoVehiculo) {
        // Reserva para reglas futuras (p. ej. bloqueo administrativo). INACTIVO no deniega.
        if (idUsuarioAutenticado <= 0 || idVehiculo <= 0) {
            throw new ErrorRegistroVehiculo("COMPARTIR_NO_PERMITIDO");
        }
        if (estadoVehiculo == null || estadoVehiculo.isBlank()) {
            throw new ErrorRegistroVehiculo("COMPARTIR_NO_PERMITIDO");
        }
    }
}
