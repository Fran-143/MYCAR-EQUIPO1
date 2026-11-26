package backend.registro.verificacion;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador Brevo (Sendinblue) vía HTTPS REST {@code POST /v3/smtp/email}.
 * Evita SMTP saliente (bloqueado en Render Free). Credenciales solo por configuración.
 */
public final class BrevoMailGateway implements MailGateway {
    private static final Logger log = LoggerFactory.getLogger(BrevoMailGateway.class);
    static final String DEFAULT_BASE_URL = "https://api.brevo.com";
    private static final String SEND_PATH = "/v3/smtp/email";
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_SLEEP_MS = 400L;

    private final RestClient client;
    private final String fromEmail;
    private final String fromName;

    public BrevoMailGateway(RestClient client, String fromEmail, String fromName) {
        this.client = client;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        log.info("MailGateway Brevo activo fromConfigured={} fromNameLen={}",
                StringUtils.hasText(fromEmail), fromName == null ? 0 : fromName.length());
    }

    @Override
    public Outcome send(Message message) {
        if (message == null || !StringUtils.hasText(message.recipient()) || !StringUtils.hasText(message.code())) {
            return new Rejected(true);
        }
        Map<String, Object> body = buildBody(message);
        Outcome last = new Uncertain();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode response = client.post()
                        .uri(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
                Accepted accepted = acceptedFrom(response);
                log.info("brevo.send ok attempt={} providerIdLen={}",
                        attempt, accepted.providerId() == null ? 0 : accepted.providerId().length());
                return accepted;
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                // No loguear body (puede reflejar remitente/OTP). Solo status HTTP.
                log.warn("brevo.send http_status={} attempt={}", status, attempt);
                if (isDefinitiveClientFailure(status)) {
                    return new Rejected(true);
                }
                last = new Uncertain();
                if (attempt < MAX_ATTEMPTS && isRetryable(status)) {
                    sleepQuietly();
                    continue;
                }
                return last;
            } catch (RestClientException ex) {
                log.warn("brevo.send transport_error attempt={} type={}",
                        attempt, ex.getClass().getSimpleName());
                last = new Uncertain();
                if (attempt < MAX_ATTEMPTS) {
                    sleepQuietly();
                    continue;
                }
                return last;
            }
        }
        return last;
    }

    private Map<String, Object> buildBody(Message message) {
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("email", fromEmail);
        if (StringUtils.hasText(fromName)) {
            sender.put("name", fromName.trim());
        }
        Map<String, Object> to = Map.of("email", message.recipient().trim());
        Map<String, Object> headers = new LinkedHashMap<>();
        String idem = message.idempotencyKey();
        if (StringUtils.hasText(idem)) {
            String key = idem.trim();
            if (key.length() > 64) {
                key = key.substring(0, 64);
            }
            headers.put("IdempotencyKey", key);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", List.of(to));
        body.put("subject", "MyCar — código de verificación");
        body.put("textContent",
                "Tu código de verificación MyCar es: " + message.code() + "\n\n"
                        + "Caduca en unos minutos. Si no solicitaste este código, ignora este correo.\n");
        if (!headers.isEmpty()) {
            body.put("headers", headers);
        }
        return body;
    }

    private static Accepted acceptedFrom(JsonNode response) {
        String id = null;
        if (response != null) {
            JsonNode messageId = response.get("messageId");
            if (messageId != null && messageId.isTextual() && StringUtils.hasText(messageId.asText())) {
                id = messageId.asText().trim();
            }
        }
        if (!StringUtils.hasText(id)) {
            id = "brevo-" + UUID.randomUUID();
        }
        if (id.length() > 128) {
            id = id.substring(0, 128);
        }
        return new Accepted(id);
    }

    private static boolean isDefinitiveClientFailure(int status) {
        return status == 400 || status == 401 || status == 403 || status == 404
                || status == 422 || status == 402;
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(RETRY_SLEEP_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
