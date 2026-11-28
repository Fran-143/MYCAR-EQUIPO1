package backend.vehiculos.service;

import backend.alertas.dao.AlertasJdbcDao;
import backend.notificaciones.service.NotificacionInternaService;
import backend.push.service.RecordatorioPushService;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tras RF14: sincroniza estados/bandeja y push por entrada de estado.
 * Corre en hilo aparte tras commit para no tumbar el HTTP de confirmar km (timeouts).
 */
@Component
public class KilometrajePushHook {
    private static final Logger log = LoggerFactory.getLogger(KilometrajePushHook.class);

    private final AlertasJdbcDao alertas;
    private final ObjectProvider<NotificacionInternaService> notificaciones;
    private final ObjectProvider<RecordatorioPushService> recordatorios;
    private final Executor postKmExecutor;

    public KilometrajePushHook(
            AlertasJdbcDao alertas,
            ObjectProvider<NotificacionInternaService> notificaciones,
            ObjectProvider<RecordatorioPushService> recordatorios,
            @Qualifier("applicationTaskExecutor") ObjectProvider<Executor> applicationExecutor) {
        this.alertas = alertas;
        this.notificaciones = notificaciones;
        this.recordatorios = recordatorios;
        Executor shared = applicationExecutor.getIfAvailable();
        this.postKmExecutor = shared != null ? shared : new SimpleAsyncTaskExecutor("rf14-post-km-");
    }

    public void afterKmActualizado(long idUsuario, long idVehiculo) {
        Runnable accion = () -> {
            try {
                alertas.sincronizar(idVehiculo, idUsuario);
                var n = notificaciones.getIfAvailable();
                if (n != null) {
                    n.sincronizarAvisosVehiculo(idVehiculo);
                }
                var r = recordatorios.getIfAvailable();
                if (r != null) {
                    int enc = r.encolarInmediatosPorKm(idVehiculo);
                    if (enc > 0) {
                        log.info("Push inmediato km vehiculo={} encolados={}", idVehiculo, enc);
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("Post-RF14 sync/push vehiculo={}: {}", idVehiculo, ex.toString());
            }
        };
        Runnable lanzarAsync = () -> postKmExecutor.execute(accion);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    lanzarAsync.run();
                }
            });
        } else {
            lanzarAsync.run();
        }
    }
}
