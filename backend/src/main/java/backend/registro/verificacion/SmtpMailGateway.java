package backend.registro.verificacion;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Adaptador SMTP real (p. ej. Gmail). Credenciales solo por configuración externa. */
public final class SmtpMailGateway implements MailGateway {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpMailGateway(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public Outcome send(Message message) {
        if (message == null || message.recipient() == null || message.code() == null) {
            return new Rejected(true);
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.recipient());
            helper.setSubject("MyCar — código de verificación");
            helper.setText(
                    "Tu código de verificación MyCar es: " + message.code() + "\n\n"
                            + "Caduca en unos minutos. Si no solicitaste este código, ignora este correo.\n",
                    false);
            mailSender.send(mime);
            String id = mime.getMessageID();
            if (id == null || id.isBlank()) {
                id = "smtp-" + UUID.randomUUID();
            }
            if (id.length() > 128) {
                id = id.substring(0, 128);
            }
            return new Accepted(id);
        } catch (MailAuthenticationException ex) {
            return new Rejected(true);
        } catch (MailSendException | MessagingException ex) {
            return new Uncertain();
        } catch (RuntimeException ex) {
            return new Uncertain();
        }
    }
}
