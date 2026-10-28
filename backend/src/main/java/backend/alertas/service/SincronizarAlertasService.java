package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.model.ErrorAlertas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF23: sincroniza antes del listado (función PostgreSQL). */
@Service
public class SincronizarAlertasService {
    private final AlertasJdbcDao alertas;

    public SincronizarAlertasService(AlertasJdbcDao alertas) {
        this.alertas = alertas;
    }

    @Transactional
    public ContratoRf22.ListaResponse listarMisAlertas(long usuario, long vehiculo) {
        if (vehiculo <= 0) throw new ErrorAlertas("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
        alertas.exigirVehiculoPropioActivo(usuario, vehiculo);
        int cambios = alertas.sincronizar(vehiculo, usuario);
        return new ContratoRf22.ListaResponse(cambios, alertas.listar(vehiculo));
    }
}
