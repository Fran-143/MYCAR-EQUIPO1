package backend.push.service;

import backend.notificaciones.dao.PreferenciaNotificacionJdbcDao;
import backend.push.PushNotificationSender;
import backend.push.PushNotificationSender.MensajePush;
import backend.push.PushNotificationSender.ResultadoEnvio;
import backend.push.dao.AlertaPushControlJdbcDao;
import backend.push.dao.DispositivoPushJdbcDao;
import backend.push.dao.OutboxPushJdbcDao;
import backend.push.dao.OutboxPushJdbcDao.OutboxFila;
import backend.push.horario.VentanaPushLima;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OutboxPushService {
    private static final Logger log = LoggerFactory.getLogger(OutboxPushService.class);

    private final OutboxPushJdbcDao outbox;
    private final DispositivoPushJdbcDao dispositivos;
    private final PushNotificationSender sender;
    private final JdbcTemplate jdbc;
    private final PreferenciaNotificacionJdbcDao preferencias;
    private final ObjectProvider<AlertaPushControlJdbcDao> pushControl;
    private final VentanaPushLima ventana;
    private final boolean simularEnvio;

    public OutboxPushService(
            OutboxPushJdbcDao outbox,
            DispositivoPushJdbcDao dispositivos,
            PushNotificationSender sender,
            JdbcTemplate jdbc,
            PreferenciaNotificacionJdbcDao preferencias,
            ObjectProvider<AlertaPushControlJdbcDao> pushControl,
            VentanaPushLima ventana,
            @Value("${mycar.fcm.simular-envio:false}") boolean simularEnvio) {
        this.outbox = outbox;
        this.dispositivos = dispositivos;
        this.sender = sender;
        this.jdbc = jdbc;
        this.preferencias = preferencias;
        this.pushControl = pushControl;
        this.ventana = ventana;
        this.simularEnvio = simularEnvio;
    }

    /** Encola tras commit. No marca control de frecuencia (solo ENVIADO lo hace). */
    public void encolarTrasCommit(
            long usuario, Long idAlerta, String categoria, String titulo, String cuerpo,
            String destino, String claveIdempotencia) {
        Runnable accion = () -> encolarAhora(
                usuario, idAlerta, categoria, titulo, cuerpo, destino, claveIdempotencia, List.of());
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

    /**
     * @return id_outbox si se insertó fila nueva; empty si ya existía la clave (no es entrega).
     */
    public Optional<Long> encolarAhora(
            long usuario, Long idAlerta, String categoria, String titulo, String cuerpo,
            String destino, String claveIdempotencia, List<Long> alertasResumen) {
        Long idNotif = resolverIdNotificacion(usuario, idAlerta);
        Optional<Long> id = outbox.encolarSiNuevo(
                usuario, idAlerta, idNotif, categoria, titulo, cuerpo, destino, claveIdempotencia);
        if (id.isPresent() && alertasResumen != null && !alertasResumen.isEmpty()) {
            outbox.vincularAlertas(id.get(), alertasResumen);
        } else if (id.isPresent() && idAlerta != null) {
            outbox.vincularAlertas(id.get(), List.of(idAlerta));
        }
        return id;
    }

    private Long resolverIdNotificacion(long usuario, Long idAlerta) {
        if (idAlerta == null) {
            return null;
        }
        return jdbc.query("""
                SELECT id_notificacion FROM mycar.notificacion
                WHERE id_usuario = ? AND id_alerta = ?
                LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, usuario, idAlerta);
    }

    /**
     * Despacha lote PENDIENTE/ERROR (todas las categorías).
     * ENVIADO ≠ encolado: solo tras FCM OK (o simular-envio) se actualiza control.
     * Concurrencia: FOR UPDATE SKIP LOCKED + intentos &lt; 8.
     */
    @Transactional
    public int procesarLote(int limite) {
        if ("NONE".equals(sender.proveedor()) && !simularEnvio) {
            return 0;
        }
        List<OutboxFila> filas = outbox.reclamarPendientes(limite);
        int enviados = 0;
        for (OutboxFila f : filas) {
            if (!rf48Permite(f.idUsuario(), f.categoria())) {
                outbox.marcar(f.idOutbox(), "OMITIDO", "rf48_off");
                continue;
            }
            if (simularEnvio || "NONE".equals(sender.proveedor())) {
                outbox.marcar(f.idOutbox(), "ENVIADO", "simulado");
                registrarControlTrasEnvio(f);
                enviados++;
                continue;
            }
            var tokens = dispositivos.listarActivos(f.idUsuario(), sender.proveedor());
            if (tokens.isEmpty()) {
                outbox.marcar(f.idOutbox(), "OMITIDO", "sin_tokens");
                continue;
            }
            boolean algunOk = false;
            boolean temporal = false;
            for (var t : tokens) {
                ResultadoEnvio r = sender.enviar(new MensajePush(
                        t.token(), f.titulo(), f.cuerpo(), f.destino(),
                        f.idAlerta(), f.idNotificacion(), null, f.categoria()));
                switch (r) {
                    case ENVIADO -> algunOk = true;
                    case TOKEN_INVALIDO -> dispositivos.desactivarPorToken(sender.proveedor(), t.token());
                    case ERROR_TEMPORAL -> temporal = true;
                    case OMITIDO -> { /* no-op */ }
                }
            }
            if (algunOk) {
                outbox.marcar(f.idOutbox(), "ENVIADO", null);
                registrarControlTrasEnvio(f);
                enviados++;
            } else if (temporal) {
                // Sigue reclamable: no se considera entregado; reintento sin nueva clave.
                outbox.marcar(f.idOutbox(), "ERROR", "temporal");
            } else {
                outbox.marcar(f.idOutbox(), "OMITIDO", "sin_entrega");
            }
        }
        return enviados;
    }

    private void registrarControlTrasEnvio(OutboxFila f) {
        if (!"MANTENIMIENTO".equals(f.categoria())) {
            return;
        }
        AlertaPushControlJdbcDao ctrl = pushControl.getIfAvailable();
        if (ctrl == null) {
            return;
        }
        var hoy = ventana.hoyLima();
        String motivo = motivoDesdeClave(f.claveIdempotencia());
        List<Long> ids = outbox.alertasDeOutbox(f.idOutbox());
        if (ids.isEmpty() && f.idAlerta() != null) {
            ids = List.of(f.idAlerta());
        }
        String estadoCiclo = estadoCicloDesdeMotivo(motivo);
        for (Long idAlerta : ids) {
            String estado = estadoCiclo != null ? estadoCiclo : estadoAlerta(idAlerta);
            if (estado != null) {
                ctrl.upsertEnvio(idAlerta, estado, hoy, motivo);
            }
        }
    }

    private String estadoAlerta(long idAlerta) {
        return jdbc.query("""
                SELECT estado FROM mycar.alerta WHERE id_alerta = ?
                """, rs -> rs.next() ? rs.getString(1) : null, idAlerta);
    }

    private static String motivoDesdeClave(String clave) {
        if (clave == null) {
            return "ENTRADA_ESTADO";
        }
        if (clave.contains(":VENCIDAS:")) {
            return "RESUMEN_VENCIDAS";
        }
        if (clave.endsWith(":INMEDIATO_KM")) {
            return "INMEDIATO_KM";
        }
        if (clave.endsWith(":RECORDATORIO")) {
            return "RECORDATORIO";
        }
        if (clave.endsWith(":ENTRADA_ESTADO")) {
            return "ENTRADA_ESTADO";
        }
        if (clave.startsWith("LEGACY:")) {
            return "ENTRADA_ESTADO";
        }
        return "ENTRADA_ESTADO";
    }

    private static String estadoCicloDesdeMotivo(String motivo) {
        if ("RESUMEN_VENCIDAS".equals(motivo)) {
            return "VENCIDA";
        }
        return null;
    }

    private boolean rf48Permite(long usuario, String categoria) {
        preferencias.asegurarFila(usuario);
        var pref = preferencias.consultar(usuario).orElse(null);
        if (pref == null || !"CONCEDIDO".equalsIgnoreCase(pref.permisoDispositivo())) {
            return false;
        }
        return switch (categoria == null ? "" : categoria) {
            case "MANTENIMIENTO" -> pref.mantenimiento();
            case "SOAT" -> pref.soat();
            case "KILOMETRAJE" -> pref.kilometraje();
            case "LICENCIA" -> true;
            default -> false;
        };
    }
}
