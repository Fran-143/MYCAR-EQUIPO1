package backend.push.fcm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.springframework.util.StringUtils;

/** Resolución de credenciales SA sin registrar el secreto. */
public final class FcmCredentials {
    private FcmCredentials() {
    }

    public static byte[] resolver(String jsonInline, String base64, String path) throws IOException {
        if (StringUtils.hasText(jsonInline)) {
            return jsonInline.getBytes(StandardCharsets.UTF_8);
        }
        if (StringUtils.hasText(base64)) {
            String cleaned = base64.trim();
            if (cleaned.length() >= 2
                    && ((cleaned.charAt(0) == '"' && cleaned.charAt(cleaned.length() - 1) == '"')
                    || (cleaned.charAt(0) == '\'' && cleaned.charAt(cleaned.length() - 1) == '\''))) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            }
            return Base64.getDecoder().decode(cleaned.replaceAll("\\s", ""));
        }
        if (StringUtils.hasText(path)) {
            Path p = Path.of(path);
            if (Files.isRegularFile(p)) {
                return Files.readAllBytes(p);
            }
        }
        return null;
    }

    public static boolean presentes(String jsonInline, String base64, String path) {
        try {
            return resolver(jsonInline, base64, path) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
