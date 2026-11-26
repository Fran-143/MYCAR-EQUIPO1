package backend.registro.verificacion;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SmtpMailGatewayTest {
    private JavaMailSender mailSender;
    private SmtpMailGateway gateway;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        gateway = new SmtpMailGateway(mailSender, "noreply@mycar.test");
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendAcceptedReturnsProviderId() {
        doAnswer(inv -> {
            MimeMessage mime = inv.getArgument(0);
            mime.setHeader("Message-ID", "<otp-fixture@mycar.test>");
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        MailGateway.Outcome outcome = gateway.send(
                new MailGateway.Message("persona@example.org", "123456", "rf01/fixture"));

        assertInstanceOf(MailGateway.Accepted.class, outcome);
        assertEquals("<otp-fixture@mycar.test>", ((MailGateway.Accepted) outcome).providerId());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendNullMessageIsRejected() {
        MailGateway.Outcome outcome = gateway.send(null);
        assertInstanceOf(MailGateway.Rejected.class, outcome);
        assertTrue(((MailGateway.Rejected) outcome).definitivelyNeverAccepted());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void authenticationFailureIsRejected() {
        doThrow(new MailAuthenticationException("auth")).when(mailSender).send(any(MimeMessage.class));

        MailGateway.Outcome outcome = gateway.send(
                new MailGateway.Message("persona@example.org", "123456", "rf01/fixture"));

        assertInstanceOf(MailGateway.Rejected.class, outcome);
        assertTrue(((MailGateway.Rejected) outcome).definitivelyNeverAccepted());
    }

    @Test
    void sendFailureIsUncertain() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        MailGateway.Outcome outcome = gateway.send(
                new MailGateway.Message("persona@example.org", "123456", "rf01/fixture"));

        assertInstanceOf(MailGateway.Uncertain.class, outcome);
    }

    @Test
    void smtpBeanRequiresFromOrUsername() {
        VerificationConfiguration config = new VerificationConfiguration();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> config.smtpMailGateway(mailSender, "", ""));
        assertTrue(ex.getMessage().contains("mycar.verificacion.mail.from"));
    }

    @Test
    void smtpBeanRequiresUsernameWhenFromPresent() {
        VerificationConfiguration config = new VerificationConfiguration();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> config.smtpMailGateway(mailSender, "noreply@mycar.test", ""));
        assertTrue(ex.getMessage().contains("spring.mail.username"));
    }
}
