package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.dao.OperacionAlertaJdbcDao;
import backend.alertas.model.ErrorAlertas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramarAlertaService {
    private final AlertasJdbcDao alertas;
    private final OperacionAlertaJdbcDao operaciones;
    private final ObjectMapper json;

    public ProgramarAlertaService(AlertasJdbcDao alertas, OperacionAlertaJdbcDao operaciones, ObjectMapper json) {
        this.alertas = alertas;
        this.operaciones = operaciones;
        this.json = json;
    }

    public ContratoRf22.ContextoResponse contexto(long usuario, long vehiculo) {
        if (vehiculo <= 0) throw new ErrorAlertas("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
        try {
            alertas.verificarVehiculoPropioActivo(usuario, vehiculo);
        } catch (Exception ex) {
            throw new ErrorAlertas("VEHICULO_NO_ENCONTRADO", "Vehículo no disponible.");
        }
        var lectura = alertas.ultimaLectura(vehiculo);
        if (lectura == null) {
            throw new ErrorAlertas("KILOMETRAJE_NO_DISPONIBLE", "No hay lectura base del vehículo.");
        }
        return new ContratoRf22.ContextoResponse(
                alertas.hoyLima(), "America/Lima",
                lectura.valorKm(), lectura.idLecturaKm(),
                alertas.tiposActivos(), alertas.referenciasRealizadas(vehiculo), true);
    }

    @Transactional
    public ContratoRf22.ProgramarResponse programar(long usuario, long vehiculo, ContratoRf22.ProgramarRequest req) {
        validar(req);
        String huella = HuellaProgramarAlerta.calcular(vehiculo, req);
        boolean nueva = operaciones.intentarCrear(req.idOperacion(), usuario, huella);
        var op = operaciones.buscarYBloquear(req.idOperacion())
                .orElseThrow(() -> new ErrorAlertas("CONFLICTO_IDEMPOTENCIA", "Operación no disponible."));
        if (op.idUsuario() != usuario || !Objects.equals(op.hashSolicitud(), huella)) {
            throw new ErrorAlertas("CONFLICTO_IDEMPOTENCIA", "Esta operación ya existe con otros datos.");
        }
        if ("CONFIRMADA".equals(op.estado())) {
            return leerConfirmada(op.respuestaJson(), req.idOperacion());
        }
        if (!nueva || !"EN_PROCESO".equals(op.estado())) {
            throw new ErrorAlertas("OPERACION_EN_PROCESO", "La operación sigue en proceso.", true);
        }

        alertas.exigirVehiculoPropioActivo(usuario, vehiculo);
        var lectura = alertas.ultimaLectura(vehiculo);
        if (lectura == null) throw new ErrorAlertas("KILOMETRAJE_NO_DISPONIBLE", "No hay lectura base del vehículo.");

        long idTipo = alertas.idTipoPorCodigo(req.codigoTipoMantenimiento());
        LocalDate fechaBase = alertas.hoyLima();
        LocalDate fechaObjetivo = null;
        Integer intervaloKm = null;
        Integer kmObjetivo = null;
        String unidad = null;
        Integer cantidad = null;
        String servicio = "OTRO".equals(req.codigoTipoMantenimiento()) ? req.servicioOtro() : null;
        if (servicio != null && servicio.isBlank()) {
            throw new ErrorAlertas("DATOS_INVALIDOS", "Describe el servicio OTRO.", Set.of("servicioOtro"));
        }

        if (req.criterio().contains("TIEMPO")) {
            unidad = req.unidadTiempo();
            cantidad = req.cantidadTiempo();
            if (unidad == null || cantidad == null) {
                throw new ErrorAlertas("DATOS_INVALIDOS", "Completa tiempo.", Set.of("unidadTiempo", "cantidadTiempo"));
            }
            fechaObjetivo = alertas.calcularFechaObjetivo(fechaBase, unidad, cantidad);
            if (!fechaObjetivo.isAfter(fechaBase)) {
                throw new ErrorAlertas("OBJETIVO_NO_FUTURO", "La fecha objetivo debe ser futura.", Set.of("cantidadTiempo"));
            }
        }
        if (req.criterio().contains("KILOMETRAJE")) {
            intervaloKm = req.intervaloKm();
            if (intervaloKm == null || intervaloKm <= 0) {
                throw new ErrorAlertas("DATOS_INVALIDOS", "Intervalo inválido.", Set.of("intervaloKm"));
            }
            kmObjetivo = lectura.valorKm() + intervaloKm;
        }

        String servicioNorm = alertas.normalizarServicioOtro(servicio == null ? "" : servicio);
        String firma = FirmaEquivalenciaAlerta.calcular(idTipo, req.criterio(), fechaObjetivo, kmObjetivo, servicioNorm);
        String estado = alertas.calcularEstadoInicial(
                req.criterio(), fechaObjetivo, fechaBase, kmObjetivo, intervaloKm, lectura.valorKm());

        try {
            long idAlerta = alertas.insertarAlerta(new AlertasJdbcDao.DatosInsercion(
                    vehiculo, idTipo, req.criterio(), servicio,
                    unidad, cantidad, fechaBase, fechaObjetivo,
                    req.criterio().contains("KILOMETRAJE") ? lectura.valorKm() : null,
                    intervaloKm, kmObjetivo, estado, firma, req.idMantenimientoReferencia()));
            alertas.historialInicial(idAlerta, estado);
            operaciones.confirmar(req.idOperacion(), usuario, idAlerta, estado);
            return new ContratoRf22.ProgramarResponse(req.idOperacion(), idAlerta, estado);
        } catch (DataIntegrityViolationException ex) {
            throw new ErrorAlertas("ALERTA_EQUIVALENTE", "Ya existe una alerta equivalente activa.");
        }
    }

    @Transactional(readOnly = true)
    public ContratoRf22.EstadoResponse consultarOperacion(long usuario, UUID idOperacion) {
        var op = operaciones.buscar(idOperacion).orElse(null);
        if (op == null || op.idUsuario() != usuario) {
            return new ContratoRf22.EstadoResponse(idOperacion, "NO_ENCONTRADA", null);
        }
        if ("CONFIRMADA".equals(op.estado())) {
            return new ContratoRf22.EstadoResponse(idOperacion, "CONFIRMADA", leerConfirmada(op.respuestaJson(), idOperacion));
        }
        return new ContratoRf22.EstadoResponse(idOperacion, op.estado(), null);
    }

    private ContratoRf22.ProgramarResponse leerConfirmada(String payload, UUID idOperacion) {
        try {
            JsonNode n = payload == null ? null : json.readTree(payload);
            if (n == null) throw new IllegalStateException("sin respuesta");
            return new ContratoRf22.ProgramarResponse(
                    idOperacion, n.get("idAlerta").asLong(), n.get("estado").asText());
        } catch (Exception ex) {
            throw new ErrorAlertas("RESPUESTA_INVALIDA", "No se pudo leer la operación confirmada.");
        }
    }

    private void validar(ContratoRf22.ProgramarRequest req) {
        if (req == null || req.idOperacion() == null) {
            throw new ErrorAlertas("DATOS_INVALIDOS", "Solicitud incompleta.");
        }
    }
}
