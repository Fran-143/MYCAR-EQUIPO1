package backend.push.service;

import backend.notificaciones.dao.PreferenciaNotificacionJdbcDao;
import backend.push.PushNotificationSender;
import backend.push.PushNotificationSender.MensajePush;
import backend.push.PushNotificationSender.ResultadoEnvio;
import backend.push.api.dto.ContratoPush;
import backend.push.dao.DispositivoPushJdbcDao;
import backend.recordatorioskm.dao.RecordatorioKmJdbcDao;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DispositivoPushService {
    private final DispositivoPushJdbcDao dao;
    private final PushNotificationSender sender;
    private final PreferenciaNotificacionJdbcDao preferencias;
    private final RecordatorioKmJdbcDao recordatoriosKm;
    private final JdbcTemplate jdbc;

    public DispositivoPushService(
            DispositivoPushJdbcDao dao,
            @Lazy PushNotificationSender sender,
            PreferenciaNotificacionJdbcDao preferencias,
            RecordatorioKmJdbcDao recordatoriosKm,
            JdbcTemplate jdbc) {
        this.dao = dao;
        this.sender = sender;
        this.preferencias = preferencias;
        this.recordatoriosKm = recordatoriosKm;
        this.jdbc = jdbc;
    }

    @Transactional
    public void registrar(long usuario, UUID instalacion, String proveedor, String token) {
        if (instalacion == null) {
            throw new IllegalArgumentException("id_instalacion requerido");
        }
        if (!StringUtils.hasText(token) || token.length() > 512) {
            throw new IllegalArgumentException("token inválido");
        }
        String prov = StringUtils.hasText(proveedor) ? proveedor.trim().toUpperCase() : "FCM";
        if (!"FCM".equals(prov)) {
            throw new IllegalArgumentException("proveedor no soportado");
        }
        dao.upsert(usuario, instalacion, prov, token.trim());
        // Registrar un dispositivo (p. ej. segundo celular) implica poder recibir.
        // Si el usuario denegó en otro aparato pero este sí registra token, promover a CONCEDIDO.
        promoverPermisoParaEnvio(usuario);
        // Tras recuperar token activo, reencolar omisiones recientes por sin_tokens (misma TX).
        dao.reencolarOmitidosSinTokens(usuario);
    }

    private void promoverPermisoParaEnvio(long usuario) {
        preferencias.asegurarFila(usuario);
        var pref = preferencias.consultar(usuario).orElse(null);
        if (pref == null) {
            return;
        }
        String actual = pref.permisoDispositivo();
        if (!"CONCEDIDO".equalsIgnoreCase(actual)) {
            preferencias.actualizarSoloPermiso(usuario, "CONCEDIDO");
        }
    }

    @Transactional
    public void desvincular(long usuario, UUID instalacion) {
        if (instalacion == null) {
            throw new IllegalArgumentException("id_instalacion requerido");
        }
        dao.desactivarPorInstalacion(usuario, instalacion);
    }

    /**
     * Envía un push de prueba únicamente a los tokens activos del usuario autenticado.
     * No hace broadcast ni usa outbox (verificación FCM controlada).
     */
    @Transactional
    public ContratoPush.ProbarResponse probar(long usuario, ContratoPush.ProbarRequest body) {
        if ("NONE".equals(sender.proveedor())) {
            throw new IllegalStateException("FCM no configurado (sin credenciales SA)");
        }
        asegurarTokenDePrueba(usuario, body);
        promoverPermisoParaEnvio(usuario);
        var tokens = dao.listarActivos(usuario, sender.proveedor());
        int enviados = 0;
        int invalidos = 0;
        int errores = 0;
        for (var t : tokens) {
            ResultadoEnvio r = sender.enviar(new MensajePush(
                    t.token(),
                    "MyCar — prueba FCM",
                    "Notificación de verificación en tu dispositivo.",
                    "NOTIFICACIONES",
                    null,
                    null,
                    null,
                    "MANTENIMIENTO"));
            switch (r) {
                case ENVIADO -> enviados++;
                case TOKEN_INVALIDO -> {
                    invalidos++;
                    dao.desactivarPorToken(sender.proveedor(), t.token());
                }
                case ERROR_TEMPORAL, OMITIDO -> errores++;
            }
        }
        return new ContratoPush.ProbarResponse(enviados > 0, tokens.size(), enviados, invalidos, errores);
    }

    /**
     * Prueba recordatorio KM: inserta en bandeja (categoría KILOMETRAJE) y FCM directo.
     * Omite outbox/ventana para poder evidenciar en cualquier horario.
     */
    @Transactional
    public ContratoPush.ProbarKmResponse probarKm(long usuario, ContratoPush.ProbarRequest body) {
        if ("NONE".equals(sender.proveedor())) {
            throw new IllegalStateException("FCM no configurado (sin credenciales SA)");
        }
        Long idVehiculo = jdbc.query("""
                SELECT id_vehiculo FROM mycar.vehiculo
                WHERE id_usuario = ?
                  AND estado_vehiculo = 'ACTIVO'
                  AND COALESCE(estado_registro, 'VIGENTE') = 'VIGENTE'
                ORDER BY id_vehiculo
                LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, usuario);
        if (idVehiculo == null) {
            throw new IllegalArgumentException("Necesitas un vehículo activo para probar recordatorios KM");
        }
        recordatoriosKm.asegurarConfigUsuario(usuario);
        jdbc.update("""
                UPDATE mycar.config_recordatorio_km_usuario
                SET estado_recordatorios = 'ACTIVO'
                WHERE id_usuario = ? AND estado_recordatorios <> 'ACTIVO'
                """, usuario);

        String clave = "KM:TEST:" + usuario + ":" + Instant.now().toEpochMilli();
        String titulo = "Prueba — actualiza el kilometraje";
        String cuerpo = "Aviso de prueba de recordatorio de kilometraje. Revisa tu bandeja en MyCar.";
        boolean bandeja = recordatoriosKm.insertarNotificacionKm(
                usuario, idVehiculo, titulo, cuerpo, clave);
        Long idNotif = jdbc.query("""
                SELECT id_notificacion FROM mycar.notificacion
                WHERE clave_idempotencia = ?
                LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, clave);

        asegurarTokenDePrueba(usuario, body);
        promoverPermisoParaEnvio(usuario);
        var tokens = dao.listarActivos(usuario, sender.proveedor());
        int enviados = 0;
        int invalidos = 0;
        int errores = 0;
        for (var t : tokens) {
            ResultadoEnvio r = sender.enviar(new MensajePush(
                    t.token(),
                    titulo,
                    cuerpo,
                    "BANDEJA",
                    null,
                    idNotif,
                    idVehiculo,
                    "KILOMETRAJE"));
            switch (r) {
                case ENVIADO -> enviados++;
                case TOKEN_INVALIDO -> {
                    invalidos++;
                    dao.desactivarPorToken(sender.proveedor(), t.token());
                }
                case ERROR_TEMPORAL, OMITIDO -> errores++;
            }
        }
        boolean ok = bandeja && enviados > 0;
        return new ContratoPush.ProbarKmResponse(
                ok, bandeja, idNotif, idVehiculo, tokens.size(), enviados, invalidos, errores);
    }

    private void asegurarTokenDePrueba(long usuario, ContratoPush.ProbarRequest body) {
        if (body == null || body.idInstalacion() == null || !StringUtils.hasText(body.token())) {
            return;
        }
        registrar(usuario, body.idInstalacion(), "FCM", body.token().trim());
    }
}
