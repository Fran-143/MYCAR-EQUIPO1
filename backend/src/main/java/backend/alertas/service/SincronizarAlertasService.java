package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.model.ErrorAlertas;
import backend.notificaciones.service.NotificacionInternaService;
import backend.push.service.RecordatorioPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

/** RF23: sincroniza antes del listado (función PostgreSQL). RF24: detalle con sync puntual. */
@Service
public class SincronizarAlertasService {
    private static final Logger log = LoggerFactory.getLogger(SincronizarAlertasService.class);
    private static final Set<String> ATENDIBLES = Set.of("PROXIMA", "PENDIENTE", "VENCIDA");
    private static final Set<String> ANULABLES = Set.of("PROGRAMADA", "PROXIMA", "PENDIENTE", "VENCIDA");

    private final AlertasJdbcDao alertas;
    private final ObjectProvider<NotificacionInternaService> notificaciones;
    private final ObjectProvider<RecordatorioPushService> pushRecordatorios;

    public SincronizarAlertasService(AlertasJdbcDao alertas) {
        this(alertas, null, null);
    }

    @Autowired
    public SincronizarAlertasService(
            AlertasJdbcDao alertas,
            ObjectProvider<NotificacionInternaService> notificaciones,
            ObjectProvider<RecordatorioPushService> pushRecordatorios) {
        this.alertas = alertas;
        this.notificaciones = notificaciones;
        this.pushRecordatorios = pushRecordatorios;
    }

    @Transactional
    public ContratoRf22.ListaResponse listarMisAlertas(long usuario, long vehiculo) {
        if (vehiculo <= 0) throw new ErrorAlertas("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
        verificarVehiculo(usuario, vehiculo);
        int cambios = sincronizarSeguro(vehiculo, usuario, false);
        return new ContratoRf22.ListaResponse(cambios, alertas.listar(vehiculo));
    }

    /** RF23: listado personal de alertas LICENCIA (sincroniza antes). */
    @Transactional
    public ContratoRf22.ListaResponse listarMisAlertasLicencia(long usuario) {
        if (usuario <= 0) throw new ErrorAlertas("SESION_NO_VALIDADA", "Sesión no válida.");
        int cambios = sincronizarLicenciaSeguro(usuario, false);
        return new ContratoRf22.ListaResponse(cambios, alertas.listarLicencia(usuario));
    }

    /** Detalle de alerta LICENCIA propia (sin Atender RF24; gestión vía Perfil → Licencia). */
    @Transactional
    public ContratoRf22.DetalleAlertaDto consultarDetalleLicencia(long usuario, long idAlerta) {
        if (usuario <= 0 || idAlerta <= 0) {
            throw new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible.");
        }
        sincronizarLicenciaSeguro(usuario, true);
        var base = alertas.obtenerDetalleLicenciaPropio(usuario, idAlerta)
                .orElseThrow(() -> new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible."));
        var acciones = new ContratoRf22.AccionesDetalleDto(false, false);
        return new ContratoRf22.DetalleAlertaDto(
                base.idAlerta(), "LICENCIA", base.codigoTipo(), base.nombreTipo(),
                base.criterio(), base.origenCalculo(), base.estado(), base.fechaEstadoUtc(),
                base.fechaObjetivo(), base.kmObjetivo(), base.kmBase(), base.intervaloKm(),
                base.unidadTiempo(), base.cantidadTiempo(), base.fechaBase(), base.servicioOtro(),
                null, null, null,
                base.versionFila(),
                null,
                acciones,
                new ContratoRf22.ContactoTallerDto(null, null, null, false, false, null, "NINGUNO", true));
    }

    /** RF24 B1: consulta detalle, sincroniza estados del vehículo y expone acciones. */
    @Transactional
    public ContratoRf22.DetalleAlertaDto consultarDetalle(long usuario, long vehiculo, long idAlerta) {
        if (vehiculo <= 0 || idAlerta <= 0) {
            throw new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible.");
        }
        verificarVehiculo(usuario, vehiculo);
        sincronizarSeguro(vehiculo, usuario, true);
        var soat = alertas.obtenerDetalleSoatPropio(usuario, vehiculo, idAlerta);
        if (soat.isPresent()) {
            var base = soat.get();
            // SOAT: sin Atender RF24 ni anulación de mantenimiento; gestión vía Configurar SOAT.
            var acciones = new ContratoRf22.AccionesDetalleDto(false, false);
            return new ContratoRf22.DetalleAlertaDto(
                    base.idAlerta(), "SOAT", base.codigoTipo(), base.nombreTipo(),
                    base.criterio(), base.origenCalculo(), base.estado(), base.fechaEstadoUtc(),
                    base.fechaObjetivo(), base.kmObjetivo(), base.kmBase(), base.intervaloKm(),
                    base.unidadTiempo(), base.cantidadTiempo(), base.fechaBase(), base.servicioOtro(),
                    null, null, null,
                    base.versionFila(),
                    new ContratoRf22.VehiculoResumenDto(
                            base.idVehiculo(), base.placa(), base.marca(), base.modelo(), base.estadoVehiculo()),
                    acciones,
                    new ContratoRf22.ContactoTallerDto(null, null, null, false, false, null, "NINGUNO", true));
        }
        var base = alertas.obtenerDetallePropio(usuario, vehiculo, idAlerta)
                .orElseThrow(() -> new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible."));
        var acciones = new ContratoRf22.AccionesDetalleDto(
                ATENDIBLES.contains(base.estado()),
                ANULABLES.contains(base.estado()));
        return new ContratoRf22.DetalleAlertaDto(
                base.idAlerta(), "MANTENIMIENTO", base.codigoTipo(), base.nombreTipo(),
                base.criterio(), base.origenCalculo(), base.estado(), base.fechaEstadoUtc(),
                base.fechaObjetivo(), base.kmObjetivo(), base.kmBase(), base.intervaloKm(),
                base.unidadTiempo(), base.cantidadTiempo(), base.fechaBase(), base.servicioOtro(),
                base.idTipoMantenimiento(), base.idMantenimientoReferencia(), base.idMantenimientoAtencion(),
                base.versionFila(),
                new ContratoRf22.VehiculoResumenDto(
                        base.idVehiculo(), base.placa(), base.marca(), base.modelo(), base.estadoVehiculo()),
                acciones,
                resolverContacto(usuario, base));
    }

    /**
     * RF24 EA09–EA14: prioridad EXPLICITO → mantenimiento ref →
     * favorito MANUAL → asociado autorizado (Prueba) → favorito → NINGUNO.
     * No marca la alerta como atendida ni muta el mantenimiento histórico.
     */
    @Transactional
    public ContratoRf22.DetalleAlertaDto elegirContactoTaller(
            long usuario, long vehiculo, long idAlerta, ContratoRf22.ElegirContactoTallerRequest body) {
        if (body == null || body.idTaller() == null || body.idTaller() <= 0) {
            throw new ErrorAlertas("DATOS_INVALIDOS", "Selecciona un taller de tu catálogo.");
        }
        exigirVehiculo(usuario, vehiculo);
        alertas.obtenerDetallePropio(usuario, vehiculo, idAlerta)
                .orElseThrow(() -> new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible."));
        alertas.tallerActivoPropio(usuario, body.idTaller())
                .orElseThrow(() -> new ErrorAlertas("TALLER_NO_DISPONIBLE", "El taller no está disponible."));
        if (!alertas.asignarTallerContacto(usuario, vehiculo, idAlerta, body.idTaller())) {
            throw new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible.");
        }
        return consultarDetalle(usuario, vehiculo, idAlerta);
    }

    private ContratoRf22.ContactoTallerDto resolverContacto(long usuario, AlertasJdbcDao.DetalleAlertaFila base) {
        if (base.idTallerContacto() != null) {
            var t = alertas.tallerActivoPropio(usuario, base.idTallerContacto()).orElse(null);
            if (t != null) {
                var c = aContacto(t, "EXPLICITO");
                if (!c.sinContactoDisponible()) return c;
                // EXPLICITO no accionable: seguir cascada (p. ej. preferir Prueba).
            }
        }
        var desdeMant = alertas.tallerDeMantenimientoReferencia(usuario, base.idMantenimientoReferencia()).orElse(null);
        if (desdeMant != null) {
            var c = aContacto(desdeMant, "MANTENIMIENTO_REF");
            if (!c.sinContactoDisponible()) return c;
            if ("MANUAL".equalsIgnoreCase(desdeMant.origen())) return c;
        }
        // Escenario alternativo: cualquier favorito (manual o asociado) tiene prioridad.
        var fav = alertas.tallerFavoritoActivo(usuario).orElse(null);
        if (fav != null) return aContacto(fav, "FAVORITO");
        // Sin favorito → taller de sistema autorizado (Prueba MyCar primero).
        var sistema = alertas.tallerAsociadoAutorizadoPreferido(usuario).orElse(null);
        if (sistema != null) return aContacto(sistema, "SISTEMA");
        return new ContratoRf22.ContactoTallerDto(null, null, null, false, false, null, "NINGUNO", true);
    }

    /**
     * Contacto accionable (teléfono / WA) solo si ASOCIADO activo + autorización explícita.
     * Si no, se redacts el teléfono para que el cliente no pueda eludir la UI.
     * (El DAO ya filtra estado ACTIVO.)
     */
    static ContratoRf22.ContactoTallerDto aContacto(
            AlertasJdbcDao.TallerContactoFila t, String origenSugerencia) {
        String tel = t.telefono() == null || t.telefono().isBlank() ? null : t.telefono().trim();
        boolean asociado = "ASOCIADO".equalsIgnoreCase(t.origen());
        boolean autorizado = asociado && t.contactoAutorizado() && tel != null && t.tieneWhatsapp();
        if (!autorizado) {
            return new ContratoRf22.ContactoTallerDto(
                    t.idTaller(), t.nombre(), null, false, false,
                    t.origen(), origenSugerencia, true);
        }
        return new ContratoRf22.ContactoTallerDto(
                t.idTaller(), t.nombre(), tel, true, true,
                t.origen(), origenSugerencia, false);
    }

    private void exigirVehiculo(long usuario, long vehiculo) {
        try {
            alertas.exigirVehiculoPropioActivo(usuario, vehiculo);
        } catch (EmptyResultDataAccessException ex) {
            throw new ErrorAlertas("VEHICULO_NO_ENCONTRADO", "Vehículo no disponible.");
        }
    }

    /** Lecturas (listado/detalle): sin FOR UPDATE; el sync SQL ya serializa alertas. */
    private void verificarVehiculo(long usuario, long vehiculo) {
        try {
            alertas.verificarVehiculoPropioActivo(usuario, vehiculo);
        } catch (EmptyResultDataAccessException ex) {
            throw new ErrorAlertas("VEHICULO_NO_ENCONTRADO", "Vehículo no disponible.");
        }
    }

    /**
     * Debounce solo en listados. Detalle siempre fuerza sync (forzar=true).
     * Tras actualizar KM el hook ya sincroniza; el debounce evita N syncs al abrir «Todos».
     */
    private static final long SYNC_DEBOUNCE_MS = 12_000L;
    private final java.util.concurrent.ConcurrentHashMap<Long, Long> ultimoSyncVehiculoMs =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, Long> ultimoSyncLicenciaMs =
            new java.util.concurrent.ConcurrentHashMap<>();

    private int sincronizarSeguro(long vehiculo, long usuario, boolean forzar) {
        try {
            long ahora = System.currentTimeMillis();
            Long previo = ultimoSyncVehiculoMs.get(vehiculo);
            if (!forzar && previo != null && ahora - previo < SYNC_DEBOUNCE_MS) {
                return 0;
            }
            int cambios = alertas.sincronizar(vehiculo, usuario);
            ultimoSyncVehiculoMs.put(vehiculo, ahora);
            // RF23: sincronizar avisos internos idempotentes tras transiciones reales.
            if (notificaciones != null) {
                var n = notificaciones.getIfAvailable();
                if (n != null) {
                    n.sincronizarAvisosVehiculo(vehiculo);
                }
            }
            if (cambios > 0) {
                programarEncoladoPushTrasCommit();
            }
            return cambios;
        } catch (EmptyResultDataAccessException ex) {
            throw new ErrorAlertas("VEHICULO_NO_ENCONTRADO", "Vehículo no disponible.");
        }
    }

    /** Tras transiciones reales (p. ej. pasa a VENCIDA): FCM al momento. */
    private void programarEncoladoPushTrasCommit() {
        if (pushRecordatorios == null) {
            return;
        }
        Runnable accion = () -> {
            var push = pushRecordatorios.getIfAvailable();
            if (push == null) {
                return;
            }
            try {
                var r = push.encolarYDespacharTrasCambio();
                log.info(
                        "Push tras sync: evaluadas={} encolados={} enviados={}",
                        r.alertasEvaluadas(), r.encolados(), r.enviados());
            } catch (RuntimeException ex) {
                log.warn("Encolado/despacho push tras sync omitido: {}", ex.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }

    private int sincronizarLicenciaSeguro(long usuario, boolean forzar) {
        try {
            long ahora = System.currentTimeMillis();
            Long previo = ultimoSyncLicenciaMs.get(usuario);
            if (!forzar && previo != null && ahora - previo < SYNC_DEBOUNCE_MS) {
                return 0;
            }
            int cambios = alertas.sincronizarLicencia(usuario);
            ultimoSyncLicenciaMs.put(usuario, ahora);
            if (notificaciones != null) {
                var n = notificaciones.getIfAvailable();
                if (n != null) {
                    n.sincronizarAvisosLicenciaUsuario(usuario);
                }
            }
            return cambios;
        } catch (EmptyResultDataAccessException ex) {
            throw new ErrorAlertas("ALERTA_NO_ENCONTRADA", "No se pudo sincronizar la licencia.");
        }
    }
}
