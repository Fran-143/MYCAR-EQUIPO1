package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.dao.OperacionAlertaJdbcDao;
import backend.alertas.model.ErrorAlertas;
import backend.mantenimiento.model.ConflictoOperacionMantenimientoException;
import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.DatosRepuesto;
import backend.mantenimiento.model.SolicitudRegistroMantenimiento;
import backend.mantenimiento.service.RegistroMantenimientoService;
import backend.notificaciones.service.NotificacionInternaService;
import backend.talleres.model.TallerNoDisponible;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF24: atender (REALIZADA) o anular manualmente; no toca el mantenimiento de referencia. */
@Service
public class AtenderAlertaService {
    private static final Set<String> ATENDIBLES = Set.of("PROXIMA", "PENDIENTE", "VENCIDA");
    private static final Set<String> ANULABLES = Set.of("PROGRAMADA", "PROXIMA", "PENDIENTE", "VENCIDA");
    private static final String TIPO_ANULAR = "ANULAR_ALERTA";

    private final AlertasJdbcDao alertas;
    private final RegistroMantenimientoService registro;
    private final OperacionAlertaJdbcDao operaciones;
    private final ObjectMapper json;
    private final ObjectProvider<NotificacionInternaService> notificaciones;

    public AtenderAlertaService(AlertasJdbcDao alertas, RegistroMantenimientoService registro,
                                OperacionAlertaJdbcDao operaciones, ObjectMapper json) {
        this(alertas, registro, operaciones, json, null);
    }

    @Autowired
    public AtenderAlertaService(AlertasJdbcDao alertas, RegistroMantenimientoService registro,
                                OperacionAlertaJdbcDao operaciones, ObjectMapper json,
                                ObjectProvider<NotificacionInternaService> notificaciones) {
        this.alertas = alertas;
        this.registro = registro;
        this.operaciones = operaciones;
        this.json = json;
        this.notificaciones = notificaciones;
    }

    @Transactional
    public ContratoRf22.AtenderResponse atender(long usuario, long vehiculo, long idAlerta,
                                                ContratoRf22.AtenderRequest body) {
        if (body == null || body.idOperacion() == null) {
            throw new ErrorAlertas("DATOS_INVALIDOS", "La operación de atención es obligatoria.");
        }
        var alerta = alertas.bloquearAlertaPropia(usuario, vehiculo, idAlerta)
                .orElseThrow(() -> new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible."));
        // Idempotencia: reintento tras éxito previo (misma alerta ya REALIZADA).
        if ("REALIZADA".equals(alerta.estado()) && alerta.idMantenimientoAtencion() != null) {
            return new ContratoRf22.AtenderResponse(
                    idAlerta, "REALIZADA", alerta.idMantenimientoAtencion(), body.idOperacion());
        }
        if (!ATENDIBLES.contains(alerta.estado())) {
            throw new ErrorAlertas("ALERTA_NO_ATENDIBLE",
                    "Solo se atienden alertas PRÓXIMA, PENDIENTE o VENCIDA.");
        }
        Long idTaller = body.idTaller();
        // Tipo y servicio OTRO vienen de la alerta (no del body); no se usa id_mantenimiento_referencia como evidencia.
        var origen = DatosRegistroMantenimiento.OrigenKmServicio.valueOf(body.origenKmServicio());
        var datos = new DatosRegistroMantenimiento(
                alerta.codigoTipo(), body.fechaServicio(), body.kmServicio(), origen,
                body.idLecturaKmReferencia(), body.costoTotal(), alerta.servicioOtro(), null, null, idTaller);
        List<DatosRepuesto> reps = body.repuestos() == null ? null : body.repuestos().stream()
                .map(r -> new DatosRepuesto(r.nombre(), r.marca(), r.cantidad(), r.precioUnitario()))
                .toList();
        var solicitud = new SolicitudRegistroMantenimiento(
                body.idOperacion(), vehiculo, datos, body.observaciones(), reps, idTaller, null);
        try {
            var resultado = registro.registrarResultado(usuario, solicitud);
            String motivo = "VENCIDA".equals(alerta.estado()) ? "ATENCION_TARDIA_RF24" : "ATENCION_RF24";
            try {
                alertas.marcarRealizada(idAlerta, alerta.estado(), alerta.versionFila(),
                        resultado.idMantenimiento(), motivo);
            } catch (IllegalStateException ex) {
                throw new ErrorAlertas("CONFLICTO_VERSION",
                        "La alerta cambió mientras se registraba la atención. Recarga e intenta de nuevo.");
            }
            if (notificaciones != null) {
                var n = notificaciones.getIfAvailable();
                if (n != null) {
                    n.marcarAtendidaPorAlerta(idAlerta);
                }
            }
            return new ContratoRf22.AtenderResponse(
                    idAlerta, "REALIZADA", resultado.idMantenimiento(), body.idOperacion());
        } catch (ConflictoOperacionMantenimientoException ex) {
            throw new ErrorAlertas("CONFLICTO_IDEMPOTENCIA",
                    "Esta operación de mantenimiento ya existe con otros datos.");
        } catch (TallerNoDisponible ex) {
            throw new ErrorAlertas("TALLER_NO_DISPONIBLE",
                    "El taller no está disponible o no te pertenece.");
        }
    }

    @Transactional
    public ContratoRf22.AnularAlertaResponse anular(long usuario, long vehiculo, long idAlerta,
                                                    ContratoRf22.AnularRequest body) {
        if (body == null || body.idOperacion() == null) {
            throw new ErrorAlertas("DATOS_INVALIDOS", "La operación de anulación es obligatoria.",
                    Set.of("idOperacion"));
        }
        String motivo = HuellaAnularAlerta.normalizar(body.motivo());
        if (motivo.isEmpty()) {
            throw new ErrorAlertas("DATOS_INVALIDOS", "Indica el motivo de la anulación.", Set.of("motivo"));
        }
        if (motivo.length() > 48) {
            throw new ErrorAlertas("DATOS_INVALIDOS", "El motivo no puede superar 48 caracteres.", Set.of("motivo"));
        }
        String huella = HuellaAnularAlerta.calcular(vehiculo, idAlerta, motivo, body.idOperacion());
        boolean nueva = operaciones.intentarCrear(body.idOperacion(), usuario, TIPO_ANULAR, huella);
        var op = operaciones.buscarYBloquear(body.idOperacion(), TIPO_ANULAR)
                .orElseThrow(() -> new ErrorAlertas("CONFLICTO_IDEMPOTENCIA", "Operación no disponible."));
        if (op.idUsuario() != usuario || !Objects.equals(op.hashSolicitud(), huella)) {
            throw new ErrorAlertas("CONFLICTO_IDEMPOTENCIA", "Esta operación ya existe con otros datos.");
        }
        if ("CONFIRMADA".equals(op.estado())) {
            return leerAnulacionConfirmada(op.respuestaJson(), body.idOperacion());
        }
        if (!nueva || !"EN_PROCESO".equals(op.estado())) {
            throw new ErrorAlertas("OPERACION_EN_PROCESO", "La operación sigue en proceso.", true);
        }

        var alerta = alertas.bloquearAlertaPropia(usuario, vehiculo, idAlerta)
                .orElseThrow(() -> new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible."));
        if ("ANULADA".equals(alerta.estado())) {
            // Misma operación reintentada tras COMMIT: ya cubierta por CONFIRMADA arriba.
            // Nueva operación sobre alerta ya anulada: no reescribe historial.
            operaciones.confirmar(body.idOperacion(), usuario, idAlerta, "ANULADA", TIPO_ANULAR);
            return new ContratoRf22.AnularAlertaResponse(idAlerta, "ANULADA", body.idOperacion());
        }
        if (!ANULABLES.contains(alerta.estado())) {
            throw new ErrorAlertas("ALERTA_NO_ANULABLE",
                    "La alerta ya no admite anulación manual.");
        }
        try {
            alertas.marcarAnuladaManual(idAlerta, alerta.estado(), alerta.versionFila(), motivo);
        } catch (IllegalStateException ex) {
            throw new ErrorAlertas("CONFLICTO_VERSION",
                    "La alerta cambió mientras se anulaba. Recarga e intenta de nuevo.");
        }
        // A08.13: desactivar avisos pendientes en la misma TX de anulación.
        if (notificaciones != null) {
            var n = notificaciones.getIfAvailable();
            if (n != null) {
                n.desactivarAvisosPendientesAlertaAnulada(idAlerta);
            }
        }
        operaciones.confirmar(body.idOperacion(), usuario, idAlerta, "ANULADA", TIPO_ANULAR);
        return new ContratoRf22.AnularAlertaResponse(idAlerta, "ANULADA", body.idOperacion());
    }

    private ContratoRf22.AnularAlertaResponse leerAnulacionConfirmada(String payload, UUID idOperacion) {
        try {
            var n = json.readTree(payload);
            return new ContratoRf22.AnularAlertaResponse(
                    n.get("idAlerta").asLong(), n.get("estado").asText(), idOperacion);
        } catch (Exception ex) {
            throw new ErrorAlertas("CONFLICTO_IDEMPOTENCIA", "No se pudo leer la anulación confirmada.");
        }
    }
}
