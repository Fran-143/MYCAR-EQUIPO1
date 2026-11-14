package backend.documentos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adaptador Supabase Storage (clave de servicio / sb_secret_).
 * Solo lee URL, bucket y secret desde configuración; nunca registra el secreto.
 */
public class SupabaseAlmacenamientoDocumentos implements AlmacenamientoDocumentos {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final String baseUrl;
    private final String bucket;
    private final String serviceSecret;
    private final HttpClient http;
    private final ObjectMapper json;

    public SupabaseAlmacenamientoDocumentos(String baseUrl, String bucket, String serviceSecret, ObjectMapper json) {
        this.baseUrl = normalizarBase(baseUrl);
        this.bucket = bucket;
        this.serviceSecret = serviceSecret;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public PresignUpload generarPresignUpload(String objectKey, String contentType, long maxBytes) {
        RutasDocumentoSoat.exigirSegmentosValidos(objectKey);
        // createSignedUploadUrl — body vacío o upsert
        JsonNode cuerpo = postJson(
                "/storage/v1/object/upload/sign/" + bucket + "/" + escaparRuta(objectKey),
                Map.of("upsert", true));
        String signed = primerTexto(cuerpo, "signedUrl", "signedURL", "url");
        String url = absolutizar(signed);
        String token = textoOpcional(cuerpo, "token");
        if (token != null && !url.contains("token=")) {
            url = url + (url.contains("?") ? "&" : "?") + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", contentType == null ? "application/octet-stream" : contentType);
        headers.put("x-upsert", "true");
        return new PresignUpload(url, headers, Instant.now().plusSeconds(7000), objectKey);
    }

    @Override
    public PresignDownload generarPresignDownload(String objectKey) {
        RutasDocumentoSoat.exigirSegmentosValidos(objectKey);
        JsonNode cuerpo = postJson(
                "/storage/v1/object/sign/" + bucket + "/" + escaparRuta(objectKey),
                Map.of("expiresIn", 300));
        String signed = primerTexto(cuerpo, "signedURL", "signedUrl", "url");
        return new PresignDownload(
                absolutizar(signed),
                Map.of(),
                Instant.now().plusSeconds(300),
                objectKey);
    }

    @Override
    public boolean confirmarExistencia(String objectKey) {
        RutasDocumentoSoat.exigirSegmentosValidos(objectKey);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/storage/v1/object/info/" + bucket + "/" + escaparRuta(objectKey)))
                    .header("Authorization", "Bearer " + serviceSecret)
                    .header("apikey", serviceSecret)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                return true;
            }
            // Fallback: listado autenticado del objeto exacto
            HttpRequest list = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/storage/v1/object/list/" + bucket))
                    .header("Authorization", "Bearer " + serviceSecret)
                    .header("apikey", serviceSecret)
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                            "prefix", objectKey,
                            "limit", 1
                    ))))
                    .build();
            HttpResponse<String> listRes = http.send(list, HttpResponse.BodyHandlers.ofString());
            if (listRes.statusCode() < 200 || listRes.statusCode() >= 300) {
                return false;
            }
            JsonNode arr = json.readTree(listRes.body());
            if (!arr.isArray()) return false;
            for (JsonNode n : arr) {
                String name = n.path("name").asText("");
                String id = n.path("id").asText("");
                if (objectKey.endsWith("/" + name) || objectKey.equals(name) || !id.isBlank()) {
                    // Con prefix=objectKey exacto, un ítem implica existencia.
                    return true;
                }
            }
            return arr.size() > 0;
        } catch (ErrorAlmacenamiento ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ErrorAlmacenamiento("STORAGE_NO_DISPONIBLE", "No se pudo verificar el documento.");
        }
    }

    @Override
    public void borrarLogicoHuerfano(String objectKey) {
        RutasDocumentoSoat.exigirSegmentosValidos(objectKey);
        try {
            String payload = json.writeValueAsString(Map.of("prefixes", java.util.List.of(objectKey)));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/storage/v1/object/" + bucket))
                    .header("Authorization", "Bearer " + serviceSecret)
                    .header("apikey", serviceSecret)
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400 && res.statusCode() != 404) {
                throw new ErrorAlmacenamiento("STORAGE_NO_DISPONIBLE", "No se pudo eliminar el objeto huérfano.");
            }
        } catch (ErrorAlmacenamiento ex) {
            throw ex;
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
            if (res.body() == null || res.body().isBlank()) {
                return json.createObjectNode();
            }
            return json.readTree(res.body());
        } catch (ErrorAlmacenamiento ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ErrorAlmacenamiento("STORAGE_NO_DISPONIBLE", "No se pudo contactar el storage.");
        }
    }

    static String normalizarBase(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL Supabase vacía");
        }
        String u = url.strip();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    String absolutizar(String signed) {
        if (signed == null || signed.isBlank()) {
            throw new ErrorAlmacenamiento("STORAGE_NO_DISPONIBLE", "Respuesta de storage inválida.");
        }
        if (signed.startsWith("http://") || signed.startsWith("https://")) {
            return signed;
        }
        if (signed.startsWith("/")) {
            return baseUrl + signed;
        }
        return baseUrl + "/" + signed;
    }

    private static String primerTexto(JsonNode nodo, String... campos) {
        for (String c : campos) {
            String v = textoOpcional(nodo, c);
            if (v != null) return v;
        }
        throw new ErrorAlmacenamiento("STORAGE_NO_DISPONIBLE", "Respuesta de storage inválida.");
    }

    private static String textoOpcional(JsonNode nodo, String campo) {
        if (nodo == null || nodo.get(campo) == null) return null;
        String v = nodo.get(campo).asText();
        return (v == null || v.isBlank()) ? null : v;
    }

    private static String escaparRuta(String objectKey) {
        String[] partes = objectKey.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(URLEncoder.encode(partes[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }
}
