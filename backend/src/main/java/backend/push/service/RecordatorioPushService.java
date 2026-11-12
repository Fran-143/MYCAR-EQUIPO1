package backend.push.service;

import backend.notificaciones.dao.PreferenciaNotificacionJdbcDao;
import backend.push.dao.AlertaPushControlJdbcDao;
import backend.push.dao.AlertaPushControlJdbcDao.Control;
import backend.push.dao.RecordatorioPushJdbcDao;
import backend.push.dao.RecordatorioPushJdbcDao.AlertaActiva;
import backend.push.horario.VentanaPushLima;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Frecuencias push mantenimiento (sustituyen el encolado en cada sync):
 * PROGRAMADA sin push; PROXIMA 1× al entrar; PENDIENTE 1× + cada 3 días;
 * VENCIDA 1× + diario; REALIZADA/ANULADA detienen.
 * Máx. 1 push/alerta/día Lima. Varias VENCIDA → un resumen por usuario.
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
        RESUMEN_VENCIDAS
    }

    private final RecordatorioPushJdbcDao alertas;
    private final AlertaPushControlJdbcDao control;
    private final OutboxPushService outbox;
    private final PreferenciaNotificacionJdbcDao preferencias;
    private final VentanaPushLima ventana;
    private final int loteDespacho;
    private final boolean forzarVentana;
    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    public RecordatorioPushService(
            RecordatorioPushJdbcDao alertas,
            AlertaPushControlJdbcDao control,
            OutboxPushService outbox,
            PreferenciaNotificacionJdbcDao preferencias,
            VentanaPushLima ventana,
            @Value("${mycar.fcm.worker-lote:25}") int loteDespacho,
            @Value("${mycar.fcm.forzar-ventana:false}") boolean forzarVentana) {
        this.alertas = alertas;
        this.control = control;
        this.outbox = outbox;
        this.preferencias = preferencias;
        this.ventana = ventana;
        this.loteDespacho = loteDespacho;
        this.forzarVentana = forzarVentana;
    }

    /**
     * Ventana 05:00+: encola recordatorios elegibles y despacha outbox.
     * No genera ráfagas de días atrasados: como máximo un ciclo por alerta/día.
     */
    public ResultadoProgramado ejecutarVentanaProgramada() {
        if (!enCurso.compareAndSet(false, true)) {
            log.info("Recordatorios omitidos: ejecución en curso");
            return new ResultadoProgramado(true, 0, 0, 0, 0);
        }
        try {
            if (!forzarVentana && !ventana.enVentanaDespacho()) {
                log.info("Fuera de ventana 05:00-21:59 Lima; no se encola ni despacha");
                return new ResultadoProgramado(false, 0, 0, 0, 0);
            }
            return encolarYDespachar(Motivo.RECORDATORIO, null);
        } finally {
            enCurso.set(false);
        }
    }

    /**
     * Tras RF14 en horario permitido: solo entradas de estado (no recordatorios periódicos).
     */
    @Transactional
    public int encolarInmediatosPorKm(long idVehiculo) {
        if (!forzarVentana && !ventana.enVentanaInmediataKm()) {
            return 0;
        }
        LocalDate hoy = ventana.hoyLima();
        int encolados = 0;
        for (AlertaActiva a : alertas.listarActivasVehiculo(idVehiculo)) {
            if ("REALIZADA".equals(a.estado()) || "ANULADA".equals(a.estado())) {
                control.detener(a.idAlerta());
                continue;
            }
            if (!rf48Ok(a.idUsuario())) {
                continue;
            }
            Elegibilidad el = evaluar(a, control.obtener(a.idAlerta()).orElse(null), hoy);
            if (el == null || el.motivo() != Motivo.ENTRADA_ESTADO) {
                continue;
            }
            String clave = claveIndividual(a.idAlerta(), hoy, Motivo.INMEDIATO_KM);
            String titulo = titulo(a.estado(), a.etiquetaServicio());
            String cuerpo = cuerpo(a.estado(), a.placa(), a.etiquetaServicio());
            // Control de frecuencia solo tras ENVIADO (OutboxPushService).
            if (outbox.encolarAhora(
                    a.idUsuario(), a.idAlerta(), "MANTENIMIENTO", titulo, cuerpo,
                    "DETALLE_ALERTA", clave, List.of(a.idAlerta())).isPresent()) {
                encolados++;
            }
        }
        if (encolados > 0) {
            outbox.procesarLote(loteDespacho);
        }
        return encolados;
    }

    private ResultadoProgramado encolarYDespachar(Motivo modoCron, Long soloVehiculo) {
        LocalDate hoy = ventana.hoyLima();
        List<AlertaActiva> todas = soloVehiculo == null
                ? alertas.listarActivasPush()
                : alertas.listarActivasVehiculo(soloVehiculo).stream()
                .filter(a -> List.of("PROXIMA", "PENDIENTE", "VENCIDA").contains(a.estado()))
                .toList();

        int omitidosRf48 = 0;
        Map<Long, List<Candidato>> vencidasPorUsuario = new LinkedHashMap<>();
        List<Candidato> individuales = new ArrayList<>();

        for (AlertaActiva a : todas) {
            if (!rf48Ok(a.idUsuario())) {
                omitidosRf48++;
                continue;
            }
            Control c = control.obtener(a.idAlerta()).orElse(null);
            if (silenciada(c, hoy)) {
                continue;
            }
            Elegibilidad el = evaluar(a, c, hoy);
            if (el == null) {
                continue;
            }
            Candidato cand = new Candidato(a, el.motivo());
            if ("VENCIDA".equals(a.estado())) {
                vencidasPorUsuario.computeIfAbsent(a.idUsuario(), k -> new ArrayList<>()).add(cand);
            } else {
                individuales.add(cand);
            }
        }

        int encolados = 0;
        for (Candidato cand : individuales) {
            encolados += encolarIndividual(cand, hoy);
        }
        for (var e : vencidasPorUsuario.entrySet()) {
            List<Candidato> grupo = e.getValue();
            if (grupo.size() == 1) {
                encolados += encolarIndividual(grupo.get(0), hoy);
            } else {
                encolados += encolarResumenVencidas(e.getKey(), grupo, hoy);
            }
        }

        int enviados = 0;
        int n;
        do {
            n = outbox.procesarLote(loteDespacho);
            enviados += n;
        } while (n > 0);

        log.info(
                "Recordatorios modo={} evaluadas={} encolados={} enviados={} rf48omit={}",
                modoCron, todas.size(), encolados, enviados, omitidosRf48);
        return new ResultadoProgramado(false, todas.size(), encolados, enviados, omitidosRf48);
    }

    private int encolarIndividual(Candidato cand, LocalDate hoy) {
        AlertaActiva a = cand.alerta();
        Motivo motivo = cand.motivo();
        String clave = claveIndividual(a.idAlerta(), hoy, motivo);
        String titulo = titulo(a.estado(), a.etiquetaServicio());
        String cuerpo = cuerpo(a.estado(), a.placa(), a.etiquetaServicio());
        return outbox.encolarAhora(
                a.idUsuario(), a.idAlerta(), "MANTENIMIENTO", titulo, cuerpo,
                "DETALLE_ALERTA", clave, List.of(a.idAlerta())).isPresent() ? 1 : 0;
    }

    private int encolarResumenVencidas(long idUsuario, List<Candidato> grupo, LocalDate hoy) {
        String clave = "MANT:USR:" + idUsuario + ":VENCIDAS:" + hoy;
        StringBuilder sb = new StringBuilder();
        List<Long> ids = new ArrayList<>();
        for (Candidato c : grupo) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(c.alerta().placa()).append('·').append(c.alerta().etiquetaServicio());
            ids.add(c.alerta().idAlerta());
        }
        String titulo = "Tienes " + grupo.size() + " mantenimientos vencidos";
        String cuerpo = sb.length() > 280 ? sb.substring(0, 277) + "..." : sb.toString();
        // id_alerta NULL en outbox; vínculo en outbox_push_alerta. Control tras ENVIADO.
        return outbox.encolarAhora(
                idUsuario, null, "MANTENIMIENTO", titulo, cuerpo, "BANDEJA", clave, ids)
                .isPresent() ? 1 : 0;
    }

    /**
     * Null = no elegible hoy.
     * Bootstrap: sin control previo en PENDIENTE/VENCIDA → ENTRADA_ESTADO (primer aviso).
     */
    Elegibilidad evaluar(AlertaActiva a, Control c, LocalDate hoy) {
        String estado = a.estado();
        if (c != null && c.fechaUltimoEnvioLima() != null && c.fechaUltimoEnvioLima().equals(hoy)) {
            return null; // máx. 1 / alerta / día
        }
        String ciclo = c == null ? null : c.estadoCiclo();
        LocalDate ultimo = c == null ? null : c.fechaUltimoEnvioLima();

        return switch (estado) {
            case "PROXIMA" -> {
                if (ciclo == null || !"PROXIMA".equals(ciclo)) {
                    yield new Elegibilidad(Motivo.ENTRADA_ESTADO);
                }
                yield null; // un solo push al entrar
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
                if (ultimo != null && ultimo.isBefore(hoy)) {
                    yield new Elegibilidad(Motivo.RECORDATORIO);
                }
                // ultimo null con ciclo VENCIDA: no debería; tratar como elegible
                if (ultimo == null) {
                    yield new Elegibilidad(Motivo.ENTRADA_ESTADO);
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

    private boolean rf48Ok(long usuario) {
        preferencias.asegurarFila(usuario);
        var pref = preferencias.consultar(usuario).orElse(null);
        return pref != null
                && pref.mantenimiento()
                && "CONCEDIDO".equalsIgnoreCase(pref.permisoDispositivo());
    }

    static String claveIndividual(long idAlerta, LocalDate hoy, Motivo motivo) {
        return "MANT:" + idAlerta + ":" + hoy + ":" + motivo.name();
    }

    private static String titulo(String estado, String servicio) {
        return switch (estado) {
            case "PROXIMA" -> "Mantenimiento próximo: " + servicio;
            case "PENDIENTE" -> "Mantenimiento pendiente: " + servicio;
            case "VENCIDA" -> "Mantenimiento vencido: " + servicio;
            default -> "Alerta de mantenimiento: " + servicio;
        };
    }

    private static String cuerpo(String estado, String placa, String servicio) {
        String base = placa + " · " + servicio;
        return switch (estado) {
            case "PROXIMA" -> "Se acerca el servicio. " + base;
            case "PENDIENTE" -> "Corresponde realizar el servicio. " + base;
            case "VENCIDA" -> "El servicio está atrasado. " + base;
            default -> base;
        };
    }

    private record Candidato(AlertaActiva alerta, Motivo motivo) {}

    record Elegibilidad(Motivo motivo) {}
}
