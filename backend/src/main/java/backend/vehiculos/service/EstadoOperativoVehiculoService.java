package backend.vehiculos.service;

import backend.vehiculos.dao.CondicionesRegistroVehiculoDao;
import backend.vehiculos.dao.VehiculoDao;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.DatosDetalleVehiculo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

/**
 * RF12 (activar/desactivar) y RF13 (establecer principal).
 * Reglas de producto acordadas: siempre ≥1 ACTIVO; no desactivar principal
 * sin transferir el principal antes vía RF13.
 */
@Service
public class EstadoOperativoVehiculoService {
    private final CondicionesRegistroVehiculoDao condiciones;
    private final VehiculoDao vehiculos;
    private final ConsultaRegistroVehiculoService consultas;

    public EstadoOperativoVehiculoService(
            CondicionesRegistroVehiculoDao condiciones,
            VehiculoDao vehiculos,
            ConsultaRegistroVehiculoService consultas
    ) {
        this.condiciones = condiciones;
        this.vehiculos = vehiculos;
        this.consultas = consultas;
    }

    @Transactional
    public DatosDetalleVehiculo establecerPrincipal(long idUsuario, long idVehiculo) {
        if (idVehiculo <= 0) {
            throw new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO");
        }
        condiciones.exigirCuentaActiva(idUsuario);
        vehiculos.bloquearVehiculosDelUsuario(idUsuario);

        var candidato = vehiculos.obtenerEstadoOperativoPropio(idUsuario, idVehiculo)
                .orElseThrow(() -> new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO"));
        if (!"ACTIVO".equals(candidato.estadoVehiculo())) {
            throw new ErrorRegistroVehiculo("CANDIDATO_NO_ELEGIBLE");
        }
        if (candidato.esPrincipal()) {
            throw new ErrorRegistroVehiculo("YA_ES_PRINCIPAL");
        }

        vehiculos.transferirPrincipal(idUsuario, idVehiculo);
        return consultas.consultarDetalle(idUsuario, idVehiculo);
    }

    @Transactional
    public DatosDetalleVehiculo cambiarEstado(long idUsuario, long idVehiculo, String estadoSolicitado) {
        if (idVehiculo <= 0) {
            throw new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO");
        }
        if (estadoSolicitado == null || estadoSolicitado.isBlank()) {
            throw new ErrorRegistroVehiculo("DATOS_INVALIDOS", Set.of("estadoVehiculo"));
        }
        String destino = estadoSolicitado.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVO".equals(destino) && !"INACTIVO".equals(destino)) {
            throw new ErrorRegistroVehiculo("DATOS_INVALIDOS", Set.of("estadoVehiculo"));
        }

        condiciones.exigirCuentaActiva(idUsuario);
        vehiculos.bloquearVehiculosDelUsuario(idUsuario);

        var actual = vehiculos.obtenerEstadoOperativoPropio(idUsuario, idVehiculo)
                .orElseThrow(() -> new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO"));

        if (destino.equals(actual.estadoVehiculo())) {
            throw new ErrorRegistroVehiculo("ESTADO_SIN_CAMBIOS");
        }

        long activos = vehiculos.contarActivos(idUsuario);

        if ("INACTIVO".equals(destino)) {
            if (activos <= 1) {
                throw new ErrorRegistroVehiculo("ULTIMO_ACTIVO");
            }
            if (actual.esPrincipal()) {
                throw new ErrorRegistroVehiculo("PRINCIPAL_REQUIERE_REEMPLAZO");
            }
            vehiculos.actualizarEstado(idUsuario, idVehiculo, "INACTIVO");
        } else {
            int limiteActivos = vehiculos.limiteActivosPlan(idUsuario);
            if (activos >= limiteActivos) {
                throw new ErrorRegistroVehiculo("LIMITE_ACTIVOS");
            }
            vehiculos.actualizarEstado(idUsuario, idVehiculo, "ACTIVO");
            if (activos == 0) {
                vehiculos.transferirPrincipal(idUsuario, idVehiculo);
            }
        }

        return consultas.consultarDetalle(idUsuario, idVehiculo);
    }
}
