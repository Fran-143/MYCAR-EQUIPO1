package backend.notificaciones.service;

import backend.alertas.dao.AlertasJdbcDao;
import backend.notificaciones.api.dto.ContratoNotificaciones;
import backend.notificaciones.dao.NotificacionJdbcDao;
import backend.notificaciones.model.ErrorNotificaciones;
import backend.push.dao.AlertaPushControlJdbcDao;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF46 consulta/filtro + RF47 marcar leída + sync interno desde RF22/23/24.
 * LEÍDA ≠ ATENDIDA. Preferencias externas (RF48) no silencian avisos internos.
 * Push FCM ya no se encola aquí: lo hace {@code RecordatorioPushService} (cron ≥05:00 / km).
 */
@Service
public class NotificacionInternaService {
    private static final Logger log = LoggerFactory.getLogger(NotificacionInternaService.class);
    private static final Set<String> CATEGORIAS = Set.of(
            "TODAS", "MANTENIMIENTO", "SOAT", "KILOMETRAJE", "LICENCIA");
    private static final Set<String> PESTANAS = Set.of("PENDIENTES", "HISTORIAL");
    private static final Set<String> FILTROS_LECTURA = Set.of("TODAS", "NO_LEIDAS", "LEIDAS");
    private static final Set<String> ACTIVAS = Set.of("PROXIMA", "PENDIENTE", "VENCIDA");
    private static final Set<String> EVALUABLES = Set.of(
            "PROGRAMADA", "PROXIMA", "PENDIENTE", "VENCIDA");

    private final NotificacionJdbcDao dao;
    private final ObjectProvider<AlertaPushControlJdbcDao> pushControl;
    private final ObjectProvider<AlertasJdbcDao> alertas;

    public NotificacionInternaService(
            NotificacionJdbcDao dao,
            ObjectProvider<AlertaPushControlJdbcDao> pushControl,
            ObjectProvider<AlertasJdbcDao> alertas) {
        this.dao = dao;
        this.pushControl = pushControl;
        this.alertas = alertas;
    }

    /** RF22 / RF23 / reconciliación RF24: crea o actualiza aviso según estado de alerta. */
    @Transactional
    public void crearOActualizarSegunEstadoAlerta(long idAlerta) {
        var ctx = dao.cargarAlerta(idAlerta).orElse(null);
        if (ctx == null) {
            return;
        }
        sincronizarContexto(ctx);
    }

    /** Tras sync RF23 de un vehículo: reconcilia avisos de todas sus alertas. */
    @Transactional
    public void sincronizarAvisosVehiculo(long idVehiculo) {
        for (var ctx : dao.listarAlertasVehiculoParaAvisos(idVehiculo)) {
            sincronizarContexto(ctx);
        }
    }

    /** Tras sync RF23 de licencia: reconcilia avisos personales LICENCIA. */
    @Transactional
    public void sincronizarAvisosLicenciaUsuario(long idUsuario) {
        for (var ctx : dao.listarAlertasLicenciaUsuarioParaAvisos(idUsuario)) {
            sincronizarContexto(ctx);
        }
    }

    /** RF24 atención: aviso activo → ATENDIDA. */
    @Transactional
    public void marcarAtendidaPorAlerta(long idAlerta) {
        dao.marcarGestionSiActiva(idAlerta, "ATENDIDA");
        detenerPush(idAlerta);
    }

    /** RF24-EA08 A08.13: desactiva avisos pendientes al anular. */
    @Transactional
    public void desactivarAvisosPendientesAlertaAnulada(long idAlerta) {
        dao.desactivarPendientesDeAlerta(idAlerta);
        detenerPush(idAlerta);
    }

    @Transactional
    public ContratoNotificaciones.BandejaResponse consultarBandeja(
            long usuario, String pestanaRaw, String categoriaRaw) {
        return consultarBandeja(usuario, pestanaRaw, categoriaRaw, "TODAS");
    }

    @Transactional
    public ContratoNotificaciones.BandejaResponse consultarBandeja(
            long usuario, String pestanaRaw, String categoriaRaw, String filtroLecturaRaw) {
        String pestana = normalizarPestana(pestanaRaw);
        String categoria = normalizarCategoria(categoriaRaw);
        String filtroLectura = normalizarFiltroLectura(filtroLecturaRaw);
        dao.conciliarVigencia(usuario);
        // Una sola fuente: título/cuerpo = estado actual de alerta (mismo que el chip).
        alinearTextosConEstadoAlerta(usuario);
        int pendientesGestion = dao.contarPendientes(usuario);
        int noLeidas = dao.contarNoLeidas(usuario);
        int leidas = dao.contarLeidas(usuario);
        var lista = dao.listar(usuario, pestana, categoria, filtroLectura);
        return new ContratoNotificaciones.BandejaResponse(
                pestana, categoria, filtroLectura, pendientesGestion, noLeidas, leidas, lista);
    }

    /** Reescribe avisos PENDIENTE para que no digan «pendiente» si la alerta ya es VENCIDA (o viceversa). */
    private void alinearTextosConEstadoAlerta(long usuario) {
        for (var ctx : dao.listarAlertasConAvisoPendienteUsuario(usuario)) {
            sincronizarContexto(ctx);
        }
    }

    @Transactional
    public ContratoNotificaciones.LeerResponse marcarLeida(long usuario, long idNotificacion) {
        var actual = dao.obtenerPropia(usuario, idNotificacion)
                .orElseThrow(() -> new ErrorNotificaciones(
                        "NOTIFICACION_NO_ENCONTRADA", "No se pudo cargar esta notificación."));
        if (!actual.leida()) {
            dao.marcarLeida(usuario, idNotificacion);
        }
        var refresco = dao.obtenerPropia(usuario, idNotificacion)
                .orElseThrow(() -> new ErrorNotificaciones(
                        "NOTIFICACION_NO_ENCONTRADA", "No se pudo cargar esta notificación."));
        return new ContratoNotificaciones.LeerResponse(
                refresco.idNotificacion(),
                refresco.leida(),
                refresco.estadoGestion(),
                refresco.destino(),
                refresco.idAlerta(),
                refresco.idVehiculo());
    }

    @Transactional
    public ContratoNotificaciones.LeerTodasResponse marcarTodasLeidas(long usuario) {
        int marcadas = dao.marcarTodasLeidas(usuario);
        return new ContratoNotificaciones.LeerTodasResponse(
                marcadas, dao.contarNoLeidas(usuario), dao.contarLeidas(usuario));
    }

    /**
     * Detalle vigente (relee alerta/KM); no muta LEÍDA.
     * Antes de armar el chip de estado ejecuta RF23 del vehículo/licencia vinculados,
     * para que «Ver alerta» no muestre PROGRAMADA/PENDIENTE cuando el listado ya es VENCIDA/REALIZADA.
     */
    @Transactional
    public ContratoNotificaciones.DetalleNotificacionDto consultarDetalle(long usuario, long idNotificacion) {
        var raw = dao.obtenerDetalleRaw(usuario, idNotificacion)
                .orElseThrow(() -> new ErrorNotificaciones(
                        "NOTIFICACION_NO_ENCONTRADA", "No se pudo cargar esta notificación."));
        sincronizarAntesDeDetalle(usuario, raw);
        raw = dao.obtenerDetalleRaw(usuario, idNotificacion)
                .orElseThrow(() -> new ErrorNotificaciones(
                        "NOTIFICACION_NO_ENCONTRADA", "No se pudo cargar esta notificación."));
        // Si la alerta pasó a REALIZADA/ANULADA, alinear gestión del aviso.
        if (raw.idAlerta() != null && raw.estadoAlerta() != null) {
            String est = raw.estadoAlerta().toUpperCase(Locale.ROOT);
            if ("REALIZADA".equals(est) && "PENDIENTE".equalsIgnoreCase(raw.estadoGestion())) {
                dao.marcarGestionSiActiva(raw.idAlerta(), "ATENDIDA");
                raw = dao.obtenerDetalleRaw(usuario, idNotificacion).orElse(raw);
            } else if ("ANULADA".equals(est) && "PENDIENTE".equalsIgnoreCase(raw.estadoGestion())) {
                dao.desactivarPendientesDeAlerta(raw.idAlerta());
                raw = dao.obtenerDetalleRaw(usuario, idNotificacion).orElse(raw);
            }
        }
        return DetalleNotificacionAssembler.ensamblar(raw, java.time.LocalDate.now(
                java.time.ZoneId.of("America/Lima")));
    }

    private void sincronizarAntesDeDetalle(long usuario, NotificacionJdbcDao.DetalleRaw raw) {
        var jdbc = alertas.getIfAvailable();
        if (jdbc == null) {
            return;
        }
        String cat = raw.categoria() == null ? "" : raw.categoria().toUpperCase(Locale.ROOT);
        try {
            if ("LICENCIA".equals(cat)) {
                jdbc.sincronizarLicencia(usuario);
                sincronizarAvisosLicenciaUsuario(usuario);
                return;
            }
            if (raw.idVehiculo() != null && raw.idVehiculo() > 0
                    && ("MANTENIMIENTO".equals(cat) || "SOAT".equals(cat)
                    || (raw.estadoAlerta() != null && EVALUABLES.contains(raw.estadoAlerta().toUpperCase(Locale.ROOT))))) {
                jdbc.sincronizar(raw.idVehiculo(), usuario);
                sincronizarAvisosVehiculo(raw.idVehiculo());
            }
        } catch (RuntimeException ex) {
            // No tumbar el detalle si el sync falla; se muestra el estado almacenado.
            log.warn("Sync RF23 previo a detalle de notificación omitido: {}", ex.toString());
        }
    }

    private void sincronizarContexto(NotificacionJdbcDao.AlertaContexto ctx) {
        String estado = ctx.estado();
        if ("PROGRAMADA".equals(estado)) {
            return;
        }
        if (ACTIVAS.contains(estado)) {
            String cat = ctx.categoria() == null ? "MANTENIMIENTO" : ctx.categoria().toUpperCase(Locale.ROOT);
            String titulo;
            String cuerpo;
            String categoriaGuardar;
            if ("SOAT".equals(cat)) {
                titulo = tituloSoat(estado);
                cuerpo = cuerpoSoat(estado, ctx.placa());
                categoriaGuardar = "SOAT";
            } else if ("LICENCIA".equals(cat)) {
                titulo = tituloLicencia(estado);
                cuerpo = cuerpoLicencia(estado);
                categoriaGuardar = "LICENCIA";
            } else {
                titulo = tituloActivo(estado, ctx.etiquetaServicio());
                cuerpo = cuerpoActivo(estado, ctx.placa(), ctx.etiquetaServicio());
                categoriaGuardar = "MANTENIMIENTO";
            }
            dao.upsertPorAlerta(ctx.idUsuario(), ctx.idAlerta(), ctx.idVehiculo(),
                    categoriaGuardar, "PENDIENTE", titulo, cuerpo);
            // Push externo: solo RecordatorioPushService (cron / km). Evita duplicados con listado.
            return;
        }
        if ("REALIZADA".equals(estado)) {
            dao.marcarGestionSiActiva(ctx.idAlerta(), "ATENDIDA");
            detenerPush(ctx.idAlerta());
            return;
        }
        if ("ANULADA".equals(estado)) {
            dao.desactivarPendientesDeAlerta(ctx.idAlerta());
            detenerPush(ctx.idAlerta());
        }
    }

    private void detenerPush(long idAlerta) {
        var c = pushControl.getIfAvailable();
        if (c != null) {
            c.detener(idAlerta);
        }
    }

    private static String tituloActivo(String estado, String servicio) {
        return switch (estado) {
            case "PROXIMA" -> "Mantenimiento próximo: " + servicio;
            case "PENDIENTE" -> "Mantenimiento pendiente: " + servicio;
            case "VENCIDA" -> "Mantenimiento vencido: " + servicio;
            default -> "Alerta de mantenimiento: " + servicio;
        };
    }

    private static String cuerpoActivo(String estado, String placa, String servicio) {
        String base = placa + " · " + servicio;
        return switch (estado) {
            case "PROXIMA" -> "Se acerca el servicio. " + base;
            case "PENDIENTE" -> "Corresponde realizar el servicio. " + base;
            case "VENCIDA" -> "El servicio está atrasado. " + base;
            default -> base;
        };
    }

    private static String tituloSoat(String estado) {
        return switch (estado) {
            case "PROXIMA" -> "Próximo vencimiento del SOAT";
            case "PENDIENTE" -> "SOAT por vencer";
            case "VENCIDA" -> "SOAT vencido";
            default -> "Aviso SOAT";
        };
    }

    private static String cuerpoSoat(String estado, String placa) {
        String p = placa == null || placa.isBlank() ? "tu vehículo" : placa;
        return switch (estado) {
            case "PROXIMA" -> "Faltan 30 días o menos para el vencimiento del SOAT de " + p + ".";
            case "PENDIENTE" -> "Corresponde renovar el SOAT de " + p + ".";
            case "VENCIDA" -> "El SOAT de " + p + " está vencido.";
            default -> "Revisa el SOAT de " + p + ".";
        };
    }

    private static String tituloLicencia(String estado) {
        return switch (estado) {
            case "PROXIMA" -> "Próximo vencimiento de tu licencia";
            case "PENDIENTE" -> "Licencia por vencer";
            case "VENCIDA" -> "Licencia vencida";
            default -> "Aviso de licencia";
        };
    }

    private static String cuerpoLicencia(String estado) {
        return switch (estado) {
            case "PROXIMA" -> "Faltan 30 días o menos para el vencimiento de tu licencia de conducir.";
            case "PENDIENTE" -> "Corresponde renovar tu licencia de conducir.";
            case "VENCIDA" -> "Tu licencia de conducir está vencida.";
            default -> "Revisa el vencimiento de tu licencia de conducir.";
        };
    }

    private static String normalizarPestana(String raw) {
        String p = raw == null || raw.isBlank() ? "PENDIENTES" : raw.trim().toUpperCase(Locale.ROOT);
        if (!PESTANAS.contains(p)) {
            throw new ErrorNotificaciones("DATOS_INVALIDOS", "Pestaña no válida.");
        }
        return p;
    }

    private static String normalizarCategoria(String raw) {
        String c = raw == null || raw.isBlank() ? "TODAS" : raw.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIAS.contains(c)) {
            throw new ErrorNotificaciones("DATOS_INVALIDOS", "Categoría no válida.");
        }
        return c;
    }

    private static String normalizarFiltroLectura(String raw) {
        String f = raw == null || raw.isBlank() ? "TODAS" : raw.trim().toUpperCase(Locale.ROOT);
        if (!FILTROS_LECTURA.contains(f)) {
            throw new ErrorNotificaciones("DATOS_INVALIDOS", "Filtro de lectura no válido.");
        }
        return f;
    }
}
