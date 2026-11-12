package backend.push.service;

import backend.push.PushNotificationSender;
import backend.push.PushNotificationSender.MensajePush;
import backend.push.PushNotificationSender.ResultadoEnvio;
import backend.push.dao.DispositivoPushJdbcDao;
import backend.push.dao.OutboxPushJdbcDao;
import backend.push.dao.OutboxPushJdbcDao.OutboxFila;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final boolean workerHabilitado;

    public OutboxPushService(
            OutboxPushJdbcDao outbox,
            DispositivoPushJdbcDao dispositivos,
            PushNotificationSender sender,
            JdbcTemplate jdbc,
            @Value("${mycar.fcm.worker-habilitado:true}") boolean workerHabilitado) {
        this.outbox = outbox;
        this.dispositivos = dispositivos;
        this.sender = sender;
        this.jdbc = jdbc;
        this.workerHabilitado = workerHabilitado;
    }

    /** Encola tras commit del upsert de notificación (evita push huérfano). */
    public void encolarTrasCommit(
            long usuario, Long idAlerta, String categoria, String titulo, String cuerpo, String destino) {
        Runnable accion = () -> {
            try {
                Long idNotif = resolverIdNotificacion(usuario, idAlerta);
                int n = outbox.encolarSiNuevo(
                        usuario, idAlerta, idNotif, categoria, titulo, cuerpo, destino);
                if (n > 0) {
                    log.debug("Outbox push encolado usuario={} alerta={}", usuario, idAlerta);
                }
            } catch (Exception e) {
                log.warn("No se pudo encolar push: {}", e.getMessage());
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

    @Transactional
    public int procesarLote(int limite) {
        if (!workerHabilitado) {
            return 0;
        }
        if ("NONE".equals(sender.proveedor())) {
            return 0;
        }
        List<OutboxFila> filas = outbox.reclamarPendientes(limite);
        int enviados = 0;
        for (OutboxFila f : filas) {
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
                enviados++;
            } else if (temporal) {
                outbox.marcar(f.idOutbox(), "ERROR", "temporal");
            } else {
                outbox.marcar(f.idOutbox(), "OMITIDO", "sin_entrega");
            }
        }
        return enviados;
    }
}
