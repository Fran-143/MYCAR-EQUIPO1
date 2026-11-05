package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.model.ErrorAlertas;
import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.DatosRepuesto;
import backend.mantenimiento.model.SolicitudRegistroMantenimiento;
import backend.mantenimiento.service.RegistroMantenimientoService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF24: atender (REALIZADA) o anular manualmente; no toca el mantenimiento de referencia. */
@Service
public class AtenderAlertaService {
    private static final Set<String> ATENDIBLES = Set.of("PROXIMA", "PENDIENTE", "VENCIDA");
    private static final Set<String> ANULABLES = Set.of("PROGRAMADA", "PROXIMA", "PENDIENTE", "VENCIDA");

    private final AlertasJdbcDao alertas;
    private final RegistroMantenimientoService registro;

    public AtenderAlertaService(AlertasJdbcDao alertas, RegistroMantenimientoService registro) {
        this.alertas = alertas;
        this.registro = registro;
    }

    @Transactional
    public ContratoRf22.AtenderResponse atender(long usuario, long vehiculo, long idAlerta,
                                                ContratoRf22.AtenderRequest body) {
        if (body == null || body.idOperacion() == null) {
            throw new ErrorAlertas("DATOS_INVALIDOS", "La operación de atención es obligatoria.");
        }
        var alerta = alertas.bloquearAlertaPropia(usuario, vehiculo, idAlerta)
                .orElseThrow(() -> new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible."));
        if (!ATENDIBLES.contains(alerta.estado())) {
            throw new ErrorAlertas("ALERTA_NO_ATENDIBLE",
                    "Solo se atienden alertas PRÓXIMA, PENDIENTE o VENCIDA.");
        }
        var origen = DatosRegistroMantenimiento.OrigenKmServicio.valueOf(body.origenKmServicio());
        var datos = new DatosRegistroMantenimiento(
                alerta.codigoTipo(), body.fechaServicio(), body.kmServicio(), origen,
                body.idLecturaKmReferencia(), body.costoTotal(), alerta.servicioOtro(), null, null);
        List<DatosRepuesto> reps = body.repuestos() == null ? null : body.repuestos().stream()
                .map(r -> new DatosRepuesto(r.nombre(), r.marca(), r.cantidad(), r.precioUnitario()))
                .toList();
        var solicitud = new SolicitudRegistroMantenimiento(
                body.idOperacion(), vehiculo, datos, body.observaciones(), reps);
        var resultado = registro.registrarResultado(usuario, solicitud);
        String motivo = "VENCIDA".equals(alerta.estado()) ? "ATENCION_TARDIA_RF24" : "ATENCION_RF24";
        alertas.marcarRealizada(idAlerta, alerta.estado(), alerta.versionFila(),
                resultado.idMantenimiento(), motivo);
        return new ContratoRf22.AtenderResponse(
                idAlerta, "REALIZADA", resultado.idMantenimiento(), body.idOperacion());
    }

    @Transactional
    public ContratoRf22.AnularAlertaResponse anular(long usuario, long vehiculo, long idAlerta) {
        var alerta = alertas.bloquearAlertaPropia(usuario, vehiculo, idAlerta)
                .orElseThrow(() -> new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible."));
        if (!ANULABLES.contains(alerta.estado())) {
            throw new ErrorAlertas("ALERTA_NO_ANULABLE",
                    "La alerta ya no admite anulación manual.");
        }
        alertas.marcarAnuladaManual(idAlerta, alerta.estado(), alerta.versionFila());
        return new ContratoRf22.AnularAlertaResponse(idAlerta, "ANULADA");
    }
}
