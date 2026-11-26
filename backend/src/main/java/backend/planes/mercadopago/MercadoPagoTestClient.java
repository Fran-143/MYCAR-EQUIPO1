package backend.planes.mercadopago;

import backend.planes.model.ErrorPlanes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Cliente Checkout Pro TEST. Sin credenciales no realiza llamadas HTTP.
 * La verificación es siempre servidor-servidor; nunca confiar en query status del deep link.
 */
@Component
public class MercadoPagoTestClient {
    private final MercadoPagoTestProperties props;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public MercadoPagoTestClient(MercadoPagoTestProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    public boolean disponible() {
        return props.operativo();
    }

    public record PreferenciaCreada(String idExterno, String initPoint, String sandboxInitPoint) { }

    public record VerificacionPago(String estadoNormalizado, String idExterno, String evidenciaSegura) { }

    public PreferenciaCreada crearPreferencia(
            String referencia,
            String titulo,
            BigDecimal importe,
            String moneda,
            String correoPagador
    ) {
        if (!props.operativo()) {
            throw new ErrorPlanes("PROVEEDOR_NO_CONFIGURADO",
                    "Mercado Pago TEST no está configurado. Usa el simulador interno.");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("external_reference", referencia);
            body.put("items", List.of(Map.of(
                    "title", titulo,
                    "quantity", 1,
                    "currency_id", moneda == null || moneda.isBlank() ? "PEN" : moneda,
                    "unit_price", importe
            )));
            body.put("payer", Map.of("email", correoPagador == null ? "test@mycar.test" : correoPagador));
            Map<String, String> backUrls = new LinkedHashMap<>();
            if (notBlank(props.getSuccessUrl())) backUrls.put("success", props.getSuccessUrl());
            if (notBlank(props.getPendingUrl())) backUrls.put("pending", props.getPendingUrl());
            if (notBlank(props.getFailureUrl())) backUrls.put("failure", props.getFailureUrl());
            if (!backUrls.isEmpty()) {
                body.put("back_urls", backUrls);
                body.put("auto_return", "approved");
            }
            body.put("notification_url", null);

            String payload = json.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(props.getApiBase()) + "/checkout/preferences"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + props.getAccessToken().strip())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new ErrorPlanes("PROVEEDOR_ERROR",
                        "Mercado Pago rechazó la preferencia (HTTP " + resp.statusCode() + ").");
            }
            JsonNode node = json.readTree(resp.body());
            String id = text(node, "id");
            String init = text(node, "init_point");
            String sandbox = text(node, "sandbox_init_point");
            if (id == null || id.isBlank()) {
                throw new ErrorPlanes("PROVEEDOR_ERROR", "Preferencia sin id externo.");
            }
            return new PreferenciaCreada(id, init, sandbox);
        } catch (ErrorPlanes e) {
            throw e;
        } catch (Exception e) {
            throw new ErrorPlanes("PROVEEDOR_ERROR",
                    "No se pudo crear preferencia Mercado Pago TEST.", true);
        }
    }

    public VerificacionPago verificarPorPreferenciaOPago(String idExterno, String referencia) {
        if (!props.operativo()) {
            throw new ErrorPlanes("PROVEEDOR_NO_CONFIGURADO",
                    "Mercado Pago TEST no está configurado.");
        }
        if (idExterno == null || idExterno.isBlank()) {
            throw new ErrorPlanes("ESTADO_INVALIDO", "La operación no tiene id externo de MP.");
        }
        try {
            // Consulta preferencia; si hay payments asociados se normaliza estado.
            HttpRequest prefReq = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(props.getApiBase()) + "/checkout/preferences/" + idExterno))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + props.getAccessToken().strip())
                    .GET()
                    .build();
            HttpResponse<String> prefResp = http.send(prefReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (prefResp.statusCode() == 404) {
                return new VerificacionPago("ERROR", idExterno, "preferencia_no_encontrada");
            }
            if (prefResp.statusCode() < 200 || prefResp.statusCode() >= 300) {
                throw new ErrorPlanes("PROVEEDOR_ERROR",
                        "Verificación MP falló (HTTP " + prefResp.statusCode() + ").", true);
            }
            JsonNode pref = json.readTree(prefResp.body());
            String extRef = text(pref, "external_reference");
            if (referencia != null && extRef != null && !referencia.equals(extRef)) {
                return new VerificacionPago("ERROR", idExterno, "external_reference_mismatch");
            }

            // Búsqueda de pagos por external_reference (S2S).
            String searchUri = trimSlash(props.getApiBase())
                    + "/v1/payments/search?external_reference="
                    + java.net.URLEncoder.encode(referencia == null ? "" : referencia, StandardCharsets.UTF_8)
                    + "&sort=date_created&criteria=desc";
            HttpRequest payReq = HttpRequest.newBuilder()
                    .uri(URI.create(searchUri))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + props.getAccessToken().strip())
                    .GET()
                    .build();
            HttpResponse<String> payResp = http.send(payReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (payResp.statusCode() >= 200 && payResp.statusCode() < 300) {
                JsonNode results = json.readTree(payResp.body()).path("results");
                if (results.isArray() && !results.isEmpty()) {
                    String status = text(results.get(0), "status");
                    return new VerificacionPago(normalizar(status), idExterno,
                            "payment_status=" + status);
                }
            }
            return new VerificacionPago("PENDIENTE", idExterno, "preferencia_sin_pagos");
        } catch (ErrorPlanes e) {
            throw e;
        } catch (Exception e) {
            throw new ErrorPlanes("PROVEEDOR_ERROR", "Error al verificar pago MP TEST.", true);
        }
    }

    public boolean webhookFirmaValida(String xSignature, String xRequestId, String dataId) {
        // Stub: sin secret configurado no valida; con secret se documenta pendiente de algoritmo MP.
        if (!notBlank(props.getWebhookSecret())) {
            return false;
        }
        return xSignature != null && !xSignature.isBlank();
    }

    private static String normalizar(String status) {
        if (status == null) return "PENDIENTE";
        return switch (status.toLowerCase()) {
            case "approved" -> "APROBADA";
            case "rejected", "cancelled" -> "RECHAZADA";
            case "refunded", "charged_back" -> "CANCELADA";
            case "in_process", "pending", "in_mediation" -> "PENDIENTE";
            default -> "PENDIENTE";
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) return "https://api.mercadopago.com";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }
}
