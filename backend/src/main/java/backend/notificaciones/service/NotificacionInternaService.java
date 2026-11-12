package backend.notificaciones.service;

import backend.notificaciones.api.dto.ContratoNotificaciones;
import backend.notificaciones.dao.NotificacionJdbcDao;
import backend.notificaciones.dao.PreferenciaNotificacionJdbcDao;
import backend.notificaciones.model.ErrorNotificaciones;
import backend.push.service.OutboxPushService;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF46 consulta/filtro + RF47 marcar leída + sync interno desde RF22/23/24.
 * LEÍDA ≠ ATENDIDA. Preferencias externas (RF48) no silencian avisos internos.
 * Maestra 08/11: filtroLectura + contadores independientes de gestión/lectura.
 * Push externo (FCM): solo tras upsert PENDIENTE y si RF48 categoría ON + permiso CONCEDIDO.
 */
@Service
public class NotificacionInternaService {
    private static final Set<String> CATEGORIAS = Set.of(
            "TODAS", "MANTENIMIENTO", "SOAT", "KILOMETRAJE", "LICENCIA");
    private static final Set<String> PESTANAS = Set.of("PENDIENTES", "HISTORIAL");
    private static final Set<String> FILTROS_LECTURA = Set.of("TODAS", "NO_LEIDAS", "LEIDAS");
    private static final Set<String> ACTIVAS = Set.of("PROXIMA", "PENDIENTE", "VENCIDA");

    private final NotificacionJdbcDao dao;
    private final PreferenciaNotificacionJdbcDao preferencias;
    private final ObjectProvider<OutboxPushService> outboxPush;

    public NotificacionInternaService(
            NotificacionJdbcDao dao,
            PreferenciaNotificacionJdbcDao preferencias,
            ObjectProvider<OutboxPushService> outboxPush) {
        this.dao = dao;
        this.preferencias = preferencias;
        this.outboxPush = outboxPush;
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
    }

    /** RF24-EA08 A08.13: desactiva avisos pendientes al anular. */
    @Transactional
    public void desactivarAvisosPendientesAlertaAnulada(long idAlerta) {
        dao.desactivarPendientesDeAlerta(idAlerta);
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
        // Destino no vigente: se puede leer igual; el cliente decide Historial/mensaje (RF47 EA).
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
            // Maestra: PROGRAMADA puede no generar aviso activo.
            return;
        }
        if (ACTIVAS.contains(estado)) {
            // RF48: preferencias ON/OFF solo afectan envío externo futuro, no la bandeja.
            String titulo = tituloActivo(estado, ctx.etiquetaServicio());
            String cuerpo = cuerpoActivo(estado, ctx.placa(), ctx.etiquetaServicio());
            // Upsert no reinicia leida (RF46-EA13).
            dao.upsertPorAlerta(ctx.idUsuario(), ctx.idAlerta(), ctx.idVehiculo(),
                    "PENDIENTE", titulo, cuerpo);
            encolarPushSiPreferencias(ctx.idUsuario(), ctx.idAlerta(), titulo, cuerpo);
            return;
        }
        if ("REALIZADA".equals(estado)) {
            // Si no había aviso, no inventamos uno solo para ATENDIDA.
            dao.marcarGestionSiActiva(ctx.idAlerta(), "ATENDIDA");
            return;
        }
        if ("ANULADA".equals(estado)) {
            dao.desactivarPendientesDeAlerta(ctx.idAlerta());
        }
    }

    private void encolarPushSiPreferencias(long usuario, long idAlerta, String titulo, String cuerpo) {
        OutboxPushService push = outboxPush.getIfAvailable();
        if (push == null) {
            return;
        }
        preferencias.asegurarFila(usuario);
        var pref = preferencias.consultar(usuario).orElse(null);
        if (pref == null || !pref.mantenimiento()) {
            return;
        }
        if (!"CONCEDIDO".equalsIgnoreCase(pref.permisoDispositivo())) {
            return;
        }
        push.encolarTrasCommit(
                usuario, idAlerta, "MANTENIMIENTO", titulo, cuerpo, "DETALLE_ALERTA");
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
