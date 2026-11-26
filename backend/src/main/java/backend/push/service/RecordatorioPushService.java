package backend.push.service;

import backend.notificaciones.dao.PreferenciaNotificacionJdbcDao;
import backend.push.dao.AlertaPushControlJdbcDao;
import backend.push.dao.AlertaPushControlJdbcDao.Control;
import backend.push.dao.DispositivoPushJdbcDao;
import backend.push.dao.RecordatorioPushJdbcDao;
import backend.push.dao.RecordatorioPushJdbcDao.AlertaActiva;
import backend.push.dao.RecordatorioPushJdbcDao.NotifKmElegible;
import backend.push.horario.VentanaPushLima;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Política push consolidada:
 * MANTENIMIENTO — PROXIMA 1×; PENDIENTE 1× + cada 3d; VENCIDA 1× + diario.
 * SOAT — hitos −30/−7/−1/+1 y semanal si VENCIDA.
 * KILOMETRAJE — según RF43 (no kilometraje_on); agrupación por categoría.
 * Encolado: puede ocurrir tras RF23 / sync (sin depender solo del cron 05:00).
 * Despacho FCM: solo dentro de 05:00–21:59 Lima (salvo forzar-ventana).
 * Agrupación: usuario + categoría + fecha Lima → 1 push si ≥2 avisos.
 * LICENCIA: sin emisor push dedicado en esta versión (documentado).
 */
@Service
public class RecordatorioPushService {
    private static final Logger log = LoggerFactory.getLogger(RecordatorioPushService.class);

    public record ResultadoProgramado(
            boolean omitidoEnCurso,
            int alertasEvaluadas,
            int encolados,
            int enviados,
            int omitidosRf48) {}

    public enum Motivo {
        ENTRADA_ESTADO,
        RECORDATORIO,
        INMEDIATO_KM,
        RESUMEN_VENCIDAS,
        RESUMEN_CATEGORIA,
        HITO_SOAT
    }

    private final RecordatorioPushJdbcDao alertas;
    private final AlertaPushControlJdbcDao control;
    private final OutboxPushService outbox;
    private final PreferenciaNotificacionJdbcDao preferencias;
    private final DispositivoPushJdbcDao dispositivos;
    private final VentanaPushLima ventana;
    private final int loteDespacho;
    private final boolean forzarVentana;
    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    public RecordatorioPushService(
            RecordatorioPushJdbcDao alertas,
            AlertaPushControlJdbcDao control,
            OutboxPushService outbox,
            PreferenciaNotificacionJdbcDao preferencias,
            DispositivoPushJdbcDao dispositivos,
            VentanaPushLima ventana,
            @Value("${mycar.fcm.worker-lote:25}") int loteDespacho,
            @Value("${mycar.fcm.forzar-ventana:false}") boolean forzarVentana) {
        this.alertas = alertas;
        this.control = control;
        this.outbox = outbox;
        this.preferencias = preferencias;
        this.dispositivos = dispositivos;
        this.ventana = ventana;
        this.loteDespacho = loteDespacho;
        this.forzarVentana = forzarVentana;
    }

    /**
     * Cron 05:00 Lima: encola candidatos y despacha dentro de ventana.
     * Fuera de ventana solo encola (el drain del worker enviará al abrir la ventana).
     */
    public ResultadoProgramado ejecutarVentanaProgramada() {
        if (!enCurso.compareAndSet(false, true)) {
            log.info("Recordatorios omitidos: ejecución en curso");
            return new ResultadoProgramado(true, 0, 0, 0, 0);
        }
        try {
            boolean despachar = forzarVentana || ventana.enVentanaDespacho();
            if (!despachar) {
                log.info("Fuera de ventana 05:00-21:59 Lima; se encola y se difiere el despacho FCM");
            }
            return encolarYDespachar(despachar);
        } finally {
            enCurso.set(false);
        }
    }

    /**
     * Tras RF23 COMPLETO o sync de vehículo: solo encola (idempotente).
     * No envía FCM aquí; OutboxPushWorker / cron despachan en ventana.
     */
    public ResultadoProgramado encolarCandidatosTrasEvaluacion() {
        if (!enCurso.compareAndSet(false, true)) {
            log.info("Encolado push omitido: ejecución en curso");
            return new ResultadoProgramado(true, 0, 0, 0, 0);
        }
        try {
            return encolarYDespachar(false);
        } finally {
            enCurso.set(false);
        }
    }

    @Transactional
    public int encolarInmediatosPorKm(long idVehiculo) {
        if (!forzarVentana && !ventana.enVentanaInmediataKm()) {
            return 0;
        }
        LocalDate hoy = ventana.hoyLima();
        int encolados = 0;
        for (AlertaActiva a : alertas.listarActivasVehiculo(idVehiculo)) {
            if (!"MANTENIMIENTO".equals(a.categoria())) {
                continue;
            }
            if ("REALIZADA".equals(a.estado()) || "ANULADA".equals(a.estado())) {
                control.detener(a.idAlerta());
                continue;
            }
            if (!prefMantOk(a.idUsuario())) {
                continue;
            }
            Elegibilidad el = evaluarMantenimiento(a, control.obtener(a.idAlerta()).orElse(null), hoy);
            if (el == null || el.motivo() != Motivo.ENTRADA_ESTADO) {
                continue;
            }
            String clave = claveIndividual("MANT", a.idAlerta(), hoy, Motivo.INMEDIATO_KM);
            if (outbox.encolarAhora(
                    a.idUsuario(), a.idAlerta(), "MANTENIMIENTO",
                    tituloMant(a.estado(), a.etiquetaServicio()),
                    cuerpoMant(a.estado(), a.placa(), a.etiquetaServicio()),
                    "BANDEJA", clave, List.of(a.idAlerta()), List.of()).isPresent()) {
                encolados++;
            }
        }
        if (encolados > 0) {
            outbox.procesarLote(loteDespacho);
        }
        return encolados;
    }

    private ResultadoProgramado encolarYDespachar(boolean despacharFcm) {
        LocalDate hoy = ventana.hoyLima();
        Map<ClaveGrupo, List<Candidato>> grupos = new LinkedHashMap<>();
        int evaluadas = 0;
        int omitidosRf48 = 0;

        for (AlertaActiva a : alertas.listarMantenimientoActivasPush()) {
            evaluadas++;
            if (!prefMantOk(a.idUsuario())) {
                omitidosRf48++;
                continue;
            }
            Control c = control.obtener(a.idAlerta()).orElse(null);
            if (silenciada(c, hoy)) {
                continue;
            }
            Elegibilidad el = evaluarMantenimiento(a, c, hoy);
            if (el == null) {
                continue;
            }
            grupos.computeIfAbsent(new ClaveGrupo(a.idUsuario(), "MANTENIMIENTO"), k -> new ArrayList<>())
                    .add(Candidato.alerta(a, el.motivo()));
        }

        for (AlertaActiva a : alertas.listarSoatActivasPush()) {
            evaluadas++;
            if (!prefSoatOk(a.idUsuario())) {
                omitidosRf48++;
                continue;
            }
            Control c = control.obtener(a.idAlerta()).orElse(null);
            if (silenciada(c, hoy)) {
                continue;
            }
            Elegibilidad el = evaluarSoat(a, c, hoy);
            if (el == null) {
                continue;
            }
            grupos.computeIfAbsent(new ClaveGrupo(a.idUsuario(), "SOAT"), k -> new ArrayList<>())
                    .add(Candidato.alerta(a, el.motivo()));
        }

        for (NotifKmElegible n : alertas.listarKilometrajeElegiblesHoy(hoy)) {
            evaluadas++;
            if (!prefKmPushOk(n.idUsuario())) {
                omitidosRf48++;
                continue;
            }
            grupos.computeIfAbsent(new ClaveGrupo(n.idUsuario(), "KILOMETRAJE"), k -> new ArrayList<>())
                    .add(Candidato.km(n));
        }

        int encolados = 0;
        for (var e : grupos.entrySet()) {
            List<Candidato> lista = e.getValue();
            if (lista.isEmpty()) {
                continue;
            }
            if (lista.size() == 1) {
                encolados += encolarIndividual(lista.get(0), hoy);
            } else {
                encolados += encolarResumen(e.getKey(), lista, hoy);
            }
        }

        int enviados = 0;
        if (despacharFcm) {
            int n;
            do {
                n = outbox.procesarLote(loteDespacho);
                enviados += n;
            } while (n > 0);
        }

        log.info(
                "Push agrupado evaluadas={} grupos={} encolados={} enviados={} rf48omit={} despachar={}",
                evaluadas, grupos.size(), encolados, enviados, omitidosRf48, despacharFcm);
        return new ResultadoProgramado(false, evaluadas, encolados, enviados, omitidosRf48);
    }

    private int encolarIndividual(Candidato cand, LocalDate hoy) {
        if (cand.alerta() != null) {
            AlertaActiva a = cand.alerta();
            String cat = a.categoria();
            String pref = "SOAT".equals(cat) ? "SOAT" : "MANT";
            String clave = claveIndividual(pref, a.idAlerta(), hoy, cand.motivo());
            String titulo;
            String cuerpo;
            if ("SOAT".equals(cat)) {
                titulo = tituloSoat(a.estado());
                cuerpo = cuerpoSoat(a.estado(), a.placa());
            } else {
                titulo = tituloMant(a.estado(), a.etiquetaServicio());
                cuerpo = cuerpoMant(a.estado(), a.placa(), a.etiquetaServicio());
            }
            return outbox.encolarAhora(
                    a.idUsuario(), a.idAlerta(), cat, titulo, cuerpo,
                    "BANDEJA", clave, List.of(a.idAlerta()), List.of()).isPresent() ? 1 : 0;
        }
        NotifKmElegible n = cand.km();
        String clave = "KM:NOTIF:" + n.idNotificacion() + ":" + hoy;
        String titulo = "Actualiza el kilometraje de tu vehículo";
        String cuerpo = "Recordatorio para " + etiquetaVehiculo(n);
        return outbox.encolarAhora(
                n.idUsuario(), null, "KILOMETRAJE", titulo, cuerpo,
                "BANDEJA", clave, List.of(), List.of(n.idNotificacion())).isPresent() ? 1 : 0;
    }

    private int encolarResumen(ClaveGrupo grupo, List<Candidato> lista, LocalDate hoy) {
        String cat = grupo.categoria();
        long usuario = grupo.idUsuario();
        String clave = cat + ":USR:" + usuario + ":" + hoy;
        List<Long> idsAlerta = lista.stream()
                .filter(c -> c.alerta() != null)
                .map(c -> c.alerta().idAlerta())
                .collect(Collectors.toList());
        List<Long> idsNotif = lista.stream()
                .filter(c -> c.km() != null)
                .map(c -> c.km().idNotificacion())
                .collect(Collectors.toList());

        String titulo;
        String cuerpo;
        switch (cat) {
            case "MANTENIMIENTO" -> {
                titulo = "Tienes " + lista.size() + " mantenimientos que revisar";
                cuerpo = lista.stream()
                        .map(c -> c.alerta().etiquetaServicio())
                        .distinct()
                        .collect(Collectors.joining(", "));
            }
            case "SOAT" -> {
                titulo = "Tienes " + lista.size() + " SOAT que revisar";
                cuerpo = "Consulta los vencimientos de tus vehículos en MyCar";
            }
            case "KILOMETRAJE" -> {
                titulo = "Actualiza el kilometraje de " + lista.size() + " vehículos";
                cuerpo = "Tienes recordatorios pendientes para " + lista.size() + " vehículos";
            }
            default -> {
                titulo = "Tienes avisos en MyCar";
                cuerpo = "Abre Notificaciones para revisarlos";
            }
        }
        if (cuerpo.length() > 280) {
            cuerpo = cuerpo.substring(0, 277) + "...";
        }
        return outbox.encolarAhora(
                usuario, null, cat, titulo, cuerpo, "BANDEJA", clave, idsAlerta, idsNotif)
                .isPresent() ? 1 : 0;
    }

    /** Null = no elegible hoy. VENCIDA mantenimiento: recordatorio diario (máx. 1/día). */
    Elegibilidad evaluarMantenimiento(AlertaActiva a, Control c, LocalDate hoy) {
        String estado = a.estado();
        if (c != null && c.fechaUltimoEnvioLima() != null && c.fechaUltimoEnvioLima().equals(hoy)) {
            return null;
        }
        String ciclo = c == null ? null : c.estadoCiclo();
        LocalDate ultimo = c == null ? null : c.fechaUltimoEnvioLima();

        return switch (estado) {
            case "PROXIMA" -> {
                if (ciclo == null || !"PROXIMA".equals(ciclo)) {
                    yield new Elegibilidad(Motivo.ENTRADA_ESTADO);
                }
                yield null;
            }
            case "PENDIENTE" -> {
                if (ciclo == null || !"PENDIENTE".equals(ciclo)) {
                    yield new Elegibilidad(Motivo.ENTRADA_ESTADO);
                }
                if (ultimo != null && ChronoUnit.DAYS.between(ultimo, hoy) >= 3) {
                    yield new Elegibilidad(Motivo.RECORDATORIO);
                }
                yield null;
            }
            case "VENCIDA" -> {
                if (ciclo == null || !"VENCIDA".equals(ciclo)) {
                    yield new Elegibilidad(Motivo.ENTRADA_ESTADO);
                }
                if (ultimo != null && ChronoUnit.DAYS.between(ultimo, hoy) >= 1) {
                    yield new Elegibilidad(Motivo.RECORDATORIO);
                }
                yield null;
            }
            default -> null;
        };
    }

    /**
     * Compat tests: delega a mantenimiento.
     * @deprecated usar {@link #evaluarMantenimiento}
     */
    Elegibilidad evaluar(AlertaActiva a, Control c, LocalDate hoy) {
        return evaluarMantenimiento(a, c, hoy);
    }

    /**
     * Hitos SOAT: −30 (PROXIMA), −7 (PENDIENTE), −1 (PENDIENTE), +1 (VENCIDA),
     * luego semanal en VENCIDA. Sin ráfaga de hitos pasados: primer elegible + calendario restante.
     */
    Elegibilidad evaluarSoat(AlertaActiva a, Control c, LocalDate hoy) {
        if (a.fechaObjetivo() == null) {
            return null;
        }
        if (c != null && c.fechaUltimoEnvioLima() != null && c.fechaUltimoEnvioLima().equals(hoy)) {
            return null;
        }
        long diasHasta = ChronoUnit.DAYS.between(hoy, a.fechaObjetivo());
        String ciclo = c == null ? null : c.estadoCiclo();
        LocalDate ultimo = c == null ? null : c.fechaUltimoEnvioLima();

        return switch (a.estado()) {
            case "PROXIMA" -> {
                // Hito ~30 días (entrada a PROXIMA)
                if (ciclo == null || !"PROXIMA".equals(ciclo)) {
                    yield new Elegibilidad(Motivo.HITO_SOAT);
                }
                yield null;
            }
            case "PENDIENTE" -> {
                if (ciclo == null || !"PENDIENTE".equals(ciclo)) {
                    // Hito 7 días (entrada PENDIENTE)
                    yield new Elegibilidad(Motivo.HITO_SOAT);
                }
                // Hito 1 día antes
                if (diasHasta == 1 && (ultimo == null || !ultimo.equals(hoy))) {
                    if (ultimo == null || ChronoUnit.DAYS.between(ultimo, hoy) >= 1) {
                        yield new Elegibilidad(Motivo.HITO_SOAT);
                    }
                }
                yield null;
            }
            case "VENCIDA" -> {
                if (ciclo == null || !"VENCIDA".equals(ciclo)) {
                    yield new Elegibilidad(Motivo.HITO_SOAT);
                }
                if (ultimo != null && ChronoUnit.DAYS.between(ultimo, hoy) >= 7) {
                    yield new Elegibilidad(Motivo.RECORDATORIO);
                }
                yield null;
            }
            default -> null;
        };
    }

    private static boolean silenciada(Control c, LocalDate hoy) {
        return c != null
                && c.silenciadoHastaLima() != null
                && !c.silenciadoHastaLima().isBefore(hoy);
    }

    private boolean prefMantOk(long usuario) {
        preferencias.asegurarFila(usuario);
        var pref = preferencias.consultar(usuario).orElse(null);
        return pref != null && pref.mantenimiento() && permisoPermiteEnvio(usuario, pref.permisoDispositivo());
    }

    private boolean prefSoatOk(long usuario) {
        preferencias.asegurarFila(usuario);
        var pref = preferencias.consultar(usuario).orElse(null);
        return pref != null && pref.soat() && permisoPermiteEnvio(usuario, pref.permisoDispositivo());
    }

    /** Push KM: interruptor RF43 + permiso dispositivo. Ignora kilometraje_on heredado. */
    private boolean prefKmPushOk(long usuario) {
        if (!alertas.rf43GlobalActivo(usuario)) {
            return false;
        }
        preferencias.asegurarFila(usuario);
        var pref = preferencias.consultar(usuario).orElse(null);
        return pref != null && permisoPermiteEnvio(usuario, pref.permisoDispositivo());
    }

    /**
     * DENEGADO bloquea. CONCEDIDO permite.
     * NO_SOLICITADO con tokens activos: cualquier celular ya registrado recibe (multi-dispositivo).
     */
    private boolean permisoPermiteEnvio(long usuario, String permiso) {
        if ("DENEGADO".equalsIgnoreCase(permiso)) {
            return false;
        }
        if ("CONCEDIDO".equalsIgnoreCase(permiso)) {
            return true;
        }
        return !dispositivos.listarActivos(usuario, "FCM").isEmpty();
    }

    static String claveIndividual(String prefijo, long idAlerta, LocalDate hoy, Motivo motivo) {
        return prefijo + ":" + idAlerta + ":" + hoy + ":" + motivo.name();
    }

    private static String etiquetaVehiculo(NotifKmElegible n) {
        String m = (n.marca() == null ? "" : n.marca()) + " " + (n.modelo() == null ? "" : n.modelo());
        return m.trim().isEmpty() ? (n.placa() == null ? "tu vehículo" : n.placa()) : m.trim();
    }

    private static String tituloMant(String estado, String servicio) {
        return switch (estado) {
            case "PROXIMA" -> "Mantenimiento próximo: " + servicio;
            case "PENDIENTE" -> "Mantenimiento pendiente: " + servicio;
            case "VENCIDA" -> "Mantenimiento vencido: " + servicio;
            default -> "Alerta de mantenimiento: " + servicio;
        };
    }

    private static String cuerpoMant(String estado, String placa, String servicio) {
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
            case "PROXIMA" -> "Revisa el SOAT de " + p + " en MyCar.";
            case "PENDIENTE" -> "El SOAT de " + p + " está por vencer.";
            case "VENCIDA" -> "El SOAT de " + p + " está vencido.";
            default -> "Revisa el SOAT de " + p + ".";
        };
    }

    private record ClaveGrupo(long idUsuario, String categoria) {}

    private record Candidato(AlertaActiva alerta, NotifKmElegible km, Motivo motivo) {
        static Candidato alerta(AlertaActiva a, Motivo m) {
            return new Candidato(a, null, m);
        }

        static Candidato km(NotifKmElegible n) {
            return new Candidato(null, n, Motivo.RECORDATORIO);
        }
    }

    record Elegibilidad(Motivo motivo) {}
}
