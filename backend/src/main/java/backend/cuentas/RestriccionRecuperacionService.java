package backend.cuentas;

import backend.localtest.SimulatedMailInbox;
import backend.registro.verificacion.MailGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * RF03 Elsa: máx. 3 solicitudes de código por ciclo → bloqueo progresivo 1h/2h/4h/24h.
 * No modifica estado_cuenta ni impide login con contraseña válida.
 */
@Service
public class RestriccionRecuperacionService {
    private static final Logger log = LoggerFactory.getLogger(RestriccionRecuperacionService.class);
    private static final int MAX_SOLICITUDES_CICLO = 3;
    private static final Duration[] BLOQUEOS = {
            Duration.ofHours(1),
            Duration.ofHours(2),
            Duration.ofHours(4),
            Duration.ofHours(24)
    };

    private final JdbcTemplate jdbc;
    private final MailGateway mail;
    private final ObjectProvider<SimulatedMailInbox> inbox;

    public RestriccionRecuperacionService(JdbcTemplate jdbc, MailGateway mail,
            ObjectProvider<SimulatedMailInbox> inbox) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.inbox = inbox;
    }

    public record EstadoRestriccion(int solicitudesCiclo, int nivelBloqueo, Instant bloqueadoHasta) {
        public boolean bloqueado(Instant now) {
            return bloqueadoHasta != null && now.isBefore(bloqueadoHasta);
        }
    }

    public Optional<EstadoRestriccion> estado(long userId) {
        return jdbc.query("""
                SELECT solicitudes_ciclo, nivel_bloqueo, bloqueado_hasta_utc
                  FROM mycar.restriccion_recuperacion WHERE id_usuario=?
                """, (r, n) -> new EstadoRestriccion(
                r.getInt(1), r.getInt(2),
                r.getTimestamp(3) == null ? null : r.getTimestamp(3).toInstant()), userId).stream().findFirst();
    }

    /** Lanza BLOQUEO_RECUPERACION si aplica; si no, registra la solicitud y puede activar bloqueo. */
    @Transactional
    public void registrarSolicitudOBloquear(long userId, String correo, Instant now, String origenHuella) {
        ensureRow(userId, now);
        var row = jdbc.query("""
                SELECT solicitudes_ciclo, nivel_bloqueo, bloqueado_hasta_utc, alerta_enviada_hasta
                  FROM mycar.restriccion_recuperacion WHERE id_usuario=? FOR UPDATE
                """, (r, n) -> new Object[]{
                r.getInt(1), r.getInt(2),
                r.getTimestamp(3) == null ? null : r.getTimestamp(3).toInstant(),
                r.getTimestamp(4) == null ? null : r.getTimestamp(4).toInstant()
        }, userId).stream().findFirst().orElseThrow();

        int solicitudes = (Integer) row[0];
        int nivel = (Integer) row[1];
        Instant bloqueadoHasta = (Instant) row[2];
        Instant alertaHasta = (Instant) row[3];

        if (bloqueadoHasta != null && now.isBefore(bloqueadoHasta)) {
            throw CuentaException.bloqueoRecuperacion(
                    "Recuperación temporalmente bloqueada. Puedes iniciar sesión con tu contraseña actual.",
                    Map.of("bloqueadoHastaUtc", bloqueadoHasta.toString(),
                            "nivelBloqueo", nivel,
                            "loginPermitido", true));
        }

        // Si el bloqueo venció, limpiar hasta y reiniciar el ciclo (una sola vez).
        Instant nuevoBloqueo = bloqueadoHasta;
        if (bloqueadoHasta != null && !now.isBefore(bloqueadoHasta)) {
            solicitudes = 0;
            nuevoBloqueo = null;
        }

        // La 3.ª solicitud del ciclo se permite; el bloqueo rige para las siguientes.
        solicitudes += 1;
        int nuevoNivel = nivel;
        boolean activarBloqueo = solicitudes >= MAX_SOLICITUDES_CICLO;
        if (activarBloqueo) {
            nuevoNivel = nivel + 1;
            Duration d = BLOQUEOS[Math.min(nuevoNivel - 1, BLOQUEOS.length - 1)];
            nuevoBloqueo = now.plus(d);
            solicitudes = 0;
        }

        jdbc.update("""
                UPDATE mycar.restriccion_recuperacion
                   SET solicitudes_ciclo=?, nivel_bloqueo=?, bloqueado_hasta_utc=?,
                       ultima_solicitud_utc=?, actualizado_utc=?
                 WHERE id_usuario=?
                """, solicitudes, nuevoNivel, utc(nuevoBloqueo), utc(now), utc(now), userId);

        jdbc.update("""
                INSERT INTO mycar.evento_restriccion_recuperacion(
                    id_usuario, tipo, nivel, bloqueado_hasta_utc, origen_huella, creado_utc)
                VALUES (?,?,?,?,?,?)
                """, userId, activarBloqueo ? "BLOQUEO_PROGRESIVO" : "SOLICITUD_CODIGO",
                nuevoNivel, utc(nuevoBloqueo), origenHuella, utc(now));

        if (activarBloqueo && nuevoBloqueo != null) {
            enviarAlertaUnaVez(userId, correo, nuevoBloqueo, alertaHasta, now);
        }
    }

    @Transactional
    public void reiniciarTrasRestablecimiento(long userId, Instant now) {
        jdbc.update("""
                UPDATE mycar.restriccion_recuperacion
                   SET solicitudes_ciclo=0, nivel_bloqueo=0, bloqueado_hasta_utc=NULL,
                       alerta_enviada_hasta=NULL, actualizado_utc=?
                 WHERE id_usuario=?
                """, utc(now), userId);
        jdbc.update("""
                INSERT INTO mycar.evento_restriccion_recuperacion(
                    id_usuario, tipo, nivel, bloqueado_hasta_utc, origen_huella, creado_utc)
                VALUES (?,'REINICIO_TRAS_RESTABLECIMIENTO',0,NULL,NULL,?)
                """, userId, utc(now));
    }

    private void ensureRow(long userId, Instant now) {
        jdbc.update("""
                INSERT INTO mycar.restriccion_recuperacion(id_usuario, finalidad, actualizado_utc)
                VALUES (?,'RECUPERAR_CONTRASENA',?)
                ON CONFLICT (id_usuario) DO NOTHING
                """, userId, utc(now));
    }

    private void enviarAlertaUnaVez(long userId, String correo, Instant hasta, Instant alertaHasta, Instant now) {
        String ventana = hasta.toString();
        Integer inserted = jdbc.queryForObject("""
                INSERT INTO mycar.alerta_seguridad_enviada(id_usuario, tipo_evento, ventana_clave, enviado_utc)
                VALUES (?, 'RECUPERACION_BLOQUEO', ?, ?)
                ON CONFLICT (id_usuario, tipo_evento, ventana_clave) DO NOTHING
                RETURNING id_alerta
                """, Integer.class, userId, ventana, utc(now));
        if (inserted == null) {
            log.info("alerta_recuperacion_duplicada userId={}", userId);
            return;
        }
        if (alertaHasta != null && alertaHasta.equals(hasta)) return;
        String when = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(hasta.atOffset(ZoneOffset.UTC));
        String body = """
                Hola:

                Detectamos múltiples solicitudes de códigos de recuperación de contraseña para tu cuenta de MyCar.
                Por seguridad, hemos bloqueado temporalmente la solicitud de nuevos códigos.

                Podrás volver a solicitar un código a partir de: %s.

                Si tú no realizaste estas solicitudes, es posible que otra persona esté intentando recuperar el acceso a tu cuenta.
                Te recomendamos contactar con Soporte MyCar.

                Importante: tu cuenta no está suspendida. Puedes seguir iniciando sesión con tu contraseña actual.

                Equipo de MyCar
                """.formatted(when);
        try {
            mail.send(new MailGateway.Message(correo, body, "rf03-alerta/" + userId + "/" + hasta.toEpochMilli()));
            jdbc.update("""
                    UPDATE mycar.restriccion_recuperacion SET alerta_enviada_hasta=? WHERE id_usuario=?
                    """, utc(hasta), userId);
        } catch (RuntimeException ex) {
            log.warn("alerta_recuperacion_mail_fallo userId={} err={}", userId, ex.toString());
        }
        SimulatedMailInbox bandeja = inbox.getIfAvailable();
        if (bandeja != null && bandeja.enabled()) {
            bandeja.registrar(correo, "ALERTA_BLOQUEO_RECUPERACION",
                    "MyCar — Alerta de bloqueo de recuperación",
                    body, null, null, userId, now, null);
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
