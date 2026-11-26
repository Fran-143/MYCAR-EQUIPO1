package backend.registro.verificacion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BrevoMailGatewayTest {
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private BrevoMailGateway gateway;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://api.brevo.test");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new BrevoMailGateway(
                builder.defaultHeader("api-key", "test-key").build(),
                "noreply@example.com",
                "MyCar");
    }

    @Test
    void sendAcceptedMapsMessageId() {
        server.expect(requestTo("https://api.brevo.test/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-key"))
                .andExpect(jsonPath("$.sender.email").value("noreply@example.com"))
                .andExpect(jsonPath("$.to[0].email").value("persona@example.org"))
                .andExpect(jsonPath("$.textContent").value(org.hamcrest.Matchers.containsString("123456")))
                .andExpect(jsonPath("$.headers.IdempotencyKey").value("rf01/fixture"))
                .andRespond(withSuccess("{\"messageId\":\"<brevo-1@relay>\"}", MediaType.APPLICATION_JSON));

        MailGateway.Outcome outcome = gateway.send(
                new MailGateway.Message("persona@example.org", "123456", "rf01/fixture"));

        assertInstanceOf(MailGateway.Accepted.class, outcome);
        assertEquals("<brevo-1@relay>", ((MailGateway.Accepted) outcome).providerId());
        server.verify();
    }

    @Test
    void unauthorizedIsRejected() {
        server.expect(anything()).andRespond(withUnauthorizedRequest());

        MailGateway.Outcome outcome = gateway.send(
                new MailGateway.Message("persona@example.org", "123456", "rf01/x"));

        assertInstanceOf(MailGateway.Rejected.class, outcome);
        assertTrue(((MailGateway.Rejected) outcome).definitivelyNeverAccepted());
        server.verify();
    }

    @Test
    void tooManyRequestsRetriesThenUncertain() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        MailGateway.Outcome outcome = gateway.send(
                new MailGateway.Message("persona@example.org", "123456", "rf01/x"));

        assertInstanceOf(MailGateway.Uncertain.class, outcome);
        server.verify();
    }

    @Test
    void nullMessageRejectedWithoutHttp() {
        MailGateway.Outcome outcome = gateway.send(null);
        assertInstanceOf(MailGateway.Rejected.class, outcome);
        server.verify();
    }

    @Test
    void mailModoOcultaCodigoEnBrevoYSmtp() {
        assertTrue(MailModo.ocultaCodigoDesarrollo("brevo"));
        assertTrue(MailModo.ocultaCodigoDesarrollo("SMTP"));
        assertFalse(MailModo.ocultaCodigoDesarrollo("desarrollo"));
        assertFalse(MailModo.ocultaCodigoDesarrollo(null));
    }

    @Test
    void brevoBeanRequiresApiKey() {
        VerificationConfiguration config = new VerificationConfiguration();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> config.brevoMailGateway("", "from@example.com", "MyCar", BrevoMailGateway.DEFAULT_BASE_URL));
        assertTrue(ex.getMessage().contains("api-key"));
    }

    @Test
    void brevoBeanRequiresFrom() {
        VerificationConfiguration config = new VerificationConfiguration();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> config.brevoMailGateway("k", "", "MyCar", BrevoMailGateway.DEFAULT_BASE_URL));
        assertTrue(ex.getMessage().contains("mail.from"));
    }
}
