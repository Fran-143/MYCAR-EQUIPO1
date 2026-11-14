package backend.documentos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Adaptador Supabase Storage (service role). Solo lee URL, bucket y secret desde configuración.
 */
public class SupabaseAlmacenamientoDocumentos implements AlmacenamientoDocumentos {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String baseUrl;
    private final String bucket;
    private final String serviceSecret;
    private final HttpClient http;
    private final ObjectMapper json;

    public SupabaseAlmacenamientoDocumentos(String baseUrl, String bucket, String serviceSecret, ObjectMapper json) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.bucket = bucket;
        this.serviceSecret = serviceSecret;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public PresignUpload generarPresignUpload(String objectKey, String contentType, long maxBytes) {
        RutasDocumentoSoat.exigirSegmentosValidos(objectKey);
        JsonNode cuerpo = postJson(
                "/storage/v1/object/upload/sign/" + bucket + "/" + escaparRuta(objectKey),
                Map.of("expiresIn", 600));
        return new PresignUpload(
                texto(cuerpo, "signedUrl"),
                Map.of("Content-Type", contentType == null ? "application/octet-stream" : contentType),
                Instant.now().plusSeconds(600),
                objectKey);
    }

    @Override
    public PresignDownload generarPresignDownload(String objectKey) {
        JsonNode cuerpo = postJson(
                "/storage/v1/object/sign/" + bucket + "/" + escaparRuta(objectKey),
                Map.of("expiresIn", 300));
        return new PresignDownload(
                texto(cuerpo, "signedURL"),
                Map.of(),
                Instant.now().plusSeconds(300),
                objectKey);
    }

    @Override
    public boolean confirmarExistencia(String objectKey) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/storage/v1/object/info/" + bucket + "/" + escaparRuta(objectKey)))
                    .header("Authorization", "Bearer " + serviceSecret)
                    .header("apikey", serviceSecret)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() >= 200 && res.statusCode() < 300;
        } catch (Exception ex) {
            throw new ErrorAlmacenamiento("STORAGE_NO_DISPONIBLE", "No se pudo verificar el documento.");
        }
    }

    @Override
    public void borrarLogicoHuerfano(String objectKey) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + escaparRuta(objectKey)))
                    .header("Authorization", "Bearer " + serviceSecret)
                    .header("apikey", serviceSecret)
                    .timeout(TIMEOUT)
                    .DELETE()
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            throw new ErrorAlmacenamiento("STORAGE_NO_DISPONIBLE", "No se pudo eliminar el objeto huérfano.");
        }
    }

    private JsonNode postJson(String path, Map<String, Object> cuerpo) {
        try {
            String payload = json.writeValueAsString(cuerpo);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Authorization", "Bearer " + serviceSecret)
                    .header("apikey", serviceSecret)
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new ErrorAlmacenamiento("STORAGE_NO_DISPONIBLE", "Storage rechazó la solicitud.");
            }
            return json.readTree(res.body());
        } catch (ErrorAlmacenamiento ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ErrorAlmacenamiento("STORAGE_NO_DISPONIBLE", "No se pudo contactar el storage.");
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        if (nodo == null || nodo.get(campo) == null || nodo.get(campo).asText().isBlank()) {
            throw new ErrorAlmacenamiento("STORAGE_NO_DISPONIBLE", "Respuesta de storage inválida.");
        }
        return nodo.get(campo).asText();
    }

    private static String escaparRuta(String objectKey) {
        String[] partes = objectKey.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(partes[i]);
        }
        return sb.toString();
    }
}