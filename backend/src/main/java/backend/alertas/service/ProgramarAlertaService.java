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
        boolean hayKm = lectura != null;
        return new ContratoRf22.ContextoResponse(
                alertas.hoyLima(), "America/Lima",
                hayKm ? lectura.valorKm() : null,
                hayKm ? lectura.idLecturaKm() : null,
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
        LocalDate hoy = alertas.hoyLima();
        var lectura = alertas.ultimaLectura(vehiculo);
        boolean desdeHoy = "DESDE_HOY".equals(req.origenCalculo());
        boolean desdeMant = "DESDE_MANTENIMIENTO".equals(req.origenCalculo());

        AlertasJdbcDao.MantenimientoBase mant = null;
        if (desdeMant) {
            if (req.idMantenimientoReferencia() == null) {
                throw new ErrorAlertas("DATOS_INVALIDOS",
                        "Selecciona un mantenimiento de referencia.", Set.of("idMantenimientoReferencia"));
            }
            mant = alertas.mantenimientoRealizado(vehiculo, req.idMantenimientoReferencia())
                    .orElseThrow(() -> new ErrorAlertas("DATOS_INVALIDOS",
                            "El mantenimiento de referencia no está disponible.",
                            Set.of("idMantenimientoReferencia")));
            if (!"REALIZADO".equals(mant.estado())) {
                throw new ErrorAlertas("DATOS_INVALIDOS",
                        "El mantenimiento de referencia no está disponible.",
                        Set.of("idMantenimientoReferencia"));
            }
        } else if (req.idMantenimientoReferencia() != null) {
            throw new ErrorAlertas("DATOS_INVALIDOS",
                    "Desde hoy no admite mantenimiento de referencia.", Set.of("idMantenimientoReferencia"));
        }

        revalidarBasesConfirmadas(req, hoy, lectura, mant);

        if (CriterioAlerta.usaKm(req.criterio()) && lectura == null) {
            throw new ErrorAlertas("KILOMETRAJE_NO_DISPONIBLE",
                    "Se necesita kilometraje vigente para programar por kilometraje o ambos.");
        }

        long idTipo = alertas.idTipoPorCodigo(req.codigoTipoMantenimiento());
        if (desdeMant && mant.idTipo() != idTipo) {
            throw new ErrorAlertas("DATOS_INVALIDOS",
                    "El mantenimiento no corresponde al tipo de servicio.",
                    Set.of("idMantenimientoReferencia"));
        }

        LocalDate fechaBase = null;
        LocalDate fechaObjetivo = null;
        Integer intervaloKm = null;
        Integer kmBase = null;
        Integer kmObjetivo = null;
        String unidad = null;
        Integer cantidad = null;
        String servicio = "OTRO".equals(req.codigoTipoMantenimiento()) ? req.servicioOtro() : null;
        if (servicio != null && servicio.isBlank()) {
            throw new ErrorAlertas("DATOS_INVALIDOS", "Describe el servicio OTRO.", Set.of("servicioOtro"));
        }
        if (desdeMant && "OTRO".equals(req.codigoTipoMantenimiento())) {
            String a = alertas.normalizarServicioOtro(servicio == null ? "" : servicio);
            String b = alertas.normalizarServicioOtro(mant.servicioOtro() == null ? "" : mant.servicioOtro());
            if (!Objects.equals(a, b)) {
                throw new ErrorAlertas("DATOS_INVALIDOS",
                        "El servicio OTRO no coincide con el mantenimiento.", Set.of("servicioOtro"));
            }
        }

        if (CriterioAlerta.usaTiempo(req.criterio())) {
            unidad = req.unidadTiempo();
            cantidad = req.cantidadTiempo();
            if (unidad == null || cantidad == null) {
                throw new ErrorAlertas("DATOS_INVALIDOS", "Completa tiempo.", Set.of("unidadTiempo", "cantidadTiempo"));
            }
            fechaBase = desdeHoy ? hoy : mant.fechaServicio();
            fechaObjetivo = alertas.calcularFechaObjetivo(fechaBase, unidad, cantidad);
            if (!fechaObjetivo.isAfter(hoy)) {
                throw new ErrorAlertas("OBJETIVO_NO_FUTURO",
                        "La fecha objetivo debe ser posterior a hoy.", Set.of("cantidadTiempo"));
            }
        }
        if (CriterioAlerta.usaKm(req.criterio())) {
            intervaloKm = req.intervaloKm();
            if (intervaloKm == null || intervaloKm <= 0) {
                throw new ErrorAlertas("DATOS_INVALIDOS", "Intervalo inválido.", Set.of("intervaloKm"));
            }
            kmBase = desdeHoy ? lectura.valorKm() : mant.kmServicio();
            if (kmBase == null || kmBase <= 0) {
                throw new ErrorAlertas("DATOS_INVALIDOS",
                        "No hay kilometraje base válido.", Set.of("intervaloKm"));
            }
            long suma = (long) kmBase + (long) intervaloKm;
            if (suma > Integer.MAX_VALUE) {
                throw new ErrorAlertas("DATOS_INVALIDOS",
                        "El kilometraje objetivo supera el máximo permitido.", Set.of("intervaloKm"));
            }
            kmObjetivo = (int) suma;
            if (kmObjetivo <= lectura.valorKm()) {
                throw new ErrorAlertas("OBJETIVO_NO_FUTURO",
                        "El kilometraje objetivo ya fue alcanzado.", Set.of("intervaloKm"));
            }
        }

        String servicioNorm = alertas.normalizarServicioOtro(servicio == null ? "" : servicio);
        String firma = FirmaEquivalenciaAlerta.calcular(idTipo, req.criterio(), fechaObjetivo, kmObjetivo, servicioNorm);
        int kmParaEstado = lectura == null ? 0 : lectura.valorKm();
        String estado = alertas.calcularEstadoInicial(
                req.criterio(), fechaObjetivo, hoy, kmObjetivo, intervaloKm, kmParaEstado);

        try {
            long idAlerta = alertas.insertarAlerta(new AlertasJdbcDao.DatosInsercion(
                    vehiculo, idTipo, req.criterio(), req.origenCalculo(), servicio,
                    unidad, cantidad, fechaBase, fechaObjetivo,
                    kmBase, intervaloKm, kmObjetivo, estado, firma,
                    desdeMant ? req.idMantenimientoReferencia() : null));
            alertas.historialInicial(idAlerta, estado);
            operaciones.confirmar(req.idOperacion(), usuario, idAlerta, estado);
            return new ContratoRf22.ProgramarResponse(req.idOperacion(), idAlerta, estado);
        } catch (DataIntegrityViolationException ex) {
            String detalle = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
            if (detalle != null && detalle.contains("uq_alerta_equivalente_activa")) {
                throw new ErrorAlertas("ALERTA_EQUIVALENTE", "Ya existe una alerta equivalente activa.");
            }
            throw new ErrorAlertas("DATOS_INVALIDOS",
                    "No se pudo guardar la alerta. Revisa el criterio y los intervalos.");
        }
    }

    private void revalidarBasesConfirmadas(
            ContratoRf22.ProgramarRequest req,
            LocalDate hoy,
            AlertasJdbcDao.LecturaActual lectura,
            AlertasJdbcDao.MantenimientoBase mant) {
        boolean desdeHoy = "DESDE_HOY".equals(req.origenCalculo());
        if (CriterioAlerta.usaTiempo(req.criterio())) {
            LocalDate esperada = desdeHoy ? hoy : mant.fechaServicio();
            if (req.fechaBaseConfirmada() == null || !req.fechaBaseConfirmada().equals(esperada)) {
                throw new ErrorAlertas("BASES_CAMBIARON",
                        "La fecha base cambió. Revisa el resumen y confirma de nuevo.");
            }
        }
        if (CriterioAlerta.usaKm(req.criterio())) {
            if (desdeHoy) {
                if (lectura == null) {
                    throw new ErrorAlertas("KILOMETRAJE_NO_DISPONIBLE",
                            "Se necesita kilometraje vigente para programar por kilometraje o ambos.");
                }
                if (req.kmBaseConfirmado() == null || req.idLecturaConfirmada() == null
                        || !Objects.equals(req.kmBaseConfirmado(), lectura.valorKm())
                        || !Objects.equals(req.idLecturaConfirmada(), lectura.idLecturaKm())) {
                    throw new ErrorAlertas("BASES_CAMBIARON",
                            "El kilometraje vigente cambió. Revisa el resumen y confirma de nuevo.");
                }
            } else {
                if (req.kmBaseConfirmado() == null
                        || !Objects.equals(req.kmBaseConfirmado(), mant.kmServicio())) {
                    throw new ErrorAlertas("BASES_CAMBIARON",
                            "El kilometraje del mantenimiento cambió. Revisa el resumen y confirma de nuevo.");
                }
            }
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
        if (req.origenCalculo() == null
                || (!"DESDE_HOY".equals(req.origenCalculo())
                && !"DESDE_MANTENIMIENTO".equals(req.origenCalculo()))) {
            throw new ErrorAlertas("DATOS_INVALIDOS", "Origen de cálculo inválido.", Set.of("origenCalculo"));
        }
    }
}
