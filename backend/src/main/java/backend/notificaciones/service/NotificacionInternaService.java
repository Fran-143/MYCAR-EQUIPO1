package backend.notificaciones.service;

import backend.notificaciones.api.dto.ContratoNotificaciones;
import backend.notificaciones.dao.NotificacionJdbcDao;
import backend.notificaciones.model.ErrorNotificaciones;
import backend.push.dao.AlertaPushControlJdbcDao;
import java.util.Locale;
import java.util.Set;
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
    private static final Set<String> CATEGORIAS = Set.of(
            "TODAS", "MANTENIMIENTO", "SOAT", "KILOMETRAJE", "LICENCIA");
    private static final Set<String> PESTANAS = Set.of("PENDIENTES", "HISTORIAL");
    private static final Set<String> FILTROS_LECTURA = Set.of("TODAS", "NO_LEIDAS", "LEIDAS");
    private static final Set<String> ACTIVAS = Set.of("PROXIMA", "PENDIENTE", "VENCIDA");

    private final NotificacionJdbcDao dao;
    private final ObjectProvider<AlertaPushControlJdbcDao> pushControl;

    public NotificacionInternaService(
            NotificacionJdbcDao dao, ObjectProvider<AlertaPushControlJdbcDao> pushControl) {
        this.dao = dao;
        this.pushControl = pushControl;
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
        int pendientesGestion = dao.contarPendientes(usuario);
        int noLeidas = dao.contarNoLeidas(usuario);
        var lista = dao.listar(usuario, pestana, categoria, filtroLectura);
        return new ContratoNotificaciones.BandejaResponse(
                pestana, categoria, filtroLectura, pendientesGestion, noLeidas, lista);
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

    private void sincronizarContexto(NotificacionJdbcDao.AlertaContexto ctx) {
        String estado = ctx.estado();
        if ("PROGRAMADA".equals(estado)) {
            return;
        }
        if (ACTIVAS.contains(estado)) {
            String titulo = tituloActivo(estado, ctx.etiquetaServicio());
            String cuerpo = cuerpoActivo(estado, ctx.placa(), ctx.etiquetaServicio());
            dao.upsertPorAlerta(ctx.idUsuario(), ctx.idAlerta(), ctx.idVehiculo(),
                    "PENDIENTE", titulo, cuerpo);
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
