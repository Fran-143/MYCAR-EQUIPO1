package backend.cuentas;

import backend.localtest.SimulatedMailInbox;
import backend.registro.verificacion.MailGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Correo de seguridad al activarse bloqueo de login (3 fallos). Una vez por ventana. */
@Service
public class AlertaLoginSeguridad {
    private static final Logger log = LoggerFactory.getLogger(AlertaLoginSeguridad.class);
    private final JdbcTemplate jdbc;
    private final MailGateway mail;
    private final ObjectProvider<SimulatedMailInbox> inbox;

    public AlertaLoginSeguridad(JdbcTemplate jdbc, MailGateway mail, ObjectProvider<SimulatedMailInbox> inbox) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.inbox = inbox;
    }

    public void notificarSiNuevoBloqueo(long userId, String correo, Instant bloqueadoHasta, Instant now) {
        if (bloqueadoHasta == null || correo == null) return;
        String ventana = bloqueadoHasta.toString();
        Integer inserted = null;
        try {
            inserted = jdbc.queryForObject("""
                    INSERT INTO mycar.alerta_seguridad_enviada(id_usuario, tipo_evento, ventana_clave, enviado_utc)
                    VALUES (?, 'LOGIN_BLOQUEO', ?, ?)
                    ON CONFLICT (id_usuario, tipo_evento, ventana_clave) DO NOTHING
                    RETURNING id_alerta
                    """, Integer.class, userId, ventana,
                    OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        } catch (Exception ex) {
            log.debug("alerta_login_insert_skip {}", ex.toString());
            return;
        }
        if (inserted == null) return;
        String when = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(bloqueadoHasta.atOffset(ZoneOffset.UTC));
        String body = """
                Hola:

                Detectamos varios intentos fallidos de inicio de sesión en tu cuenta de MyCar.
                Por seguridad, el acceso quedó bloqueado temporalmente.

                Podrás volver a intentar a partir de: %s.

                Si no reconoces esta actividad, contacta con Soporte MyCar.

                Equipo de MyCar
                """.formatted(when);
        try {
            mail.send(new MailGateway.Message(correo, body, "login-alerta/" + userId + "/" + bloqueadoHasta.toEpochMilli()));
        } catch (RuntimeException ex) {
            log.warn("alerta_login_mail_fallo userId={} err={}", userId, ex.toString());
        }
        SimulatedMailInbox bandeja = inbox.getIfAvailable();
        if (bandeja != null && bandeja.enabled()) {
            bandeja.registrar(correo, "ALERTA_BLOQUEO_LOGIN",
                    "MyCar — Alerta de bloqueo de inicio de sesión",
                    body, null, null, userId, now, null);
        }
    }
}
