package backend.documentos;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Prefijo canónico: licencia/{idUsuario}/{idLicencia|temp}/… */
public final class RutasDocumentoLicencia {
    private static final Pattern SEGMENTO = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private RutasDocumentoLicencia() { }

    public static String prefijoTemp(long idUsuario) {
        return "licencia/" + idUsuario + "/temp";
    }

    public static String prefijoLicencia(long idUsuario, long idLicencia) {
        return "licencia/" + idUsuario + "/" + idLicencia;
    }

    public static String claveTemp(long idUsuario, String lado, String nombreSeguro) {
        return prefijoTemp(idUsuario) + "/" + lado.toLowerCase(Locale.ROOT) + "/"
                + UUID.randomUUID() + "_" + nombreSeguro;
    }

    public static String claveLicencia(long idUsuario, long idLicencia, String lado, String nombreSeguro) {
        return prefijoLicencia(idUsuario, idLicencia) + "/" + lado.toLowerCase(Locale.ROOT) + "/"
                + nombreSeguro;
    }

    public static boolean perteneceAUsuario(String objectKey, long idUsuario) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }
        String esperado = "licencia/" + idUsuario + "/";
        return objectKey.startsWith(esperado);
    }

    public static String nombreSeguro(String nombreOriginal) {
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            return "foto";
        }
        String base = nombreOriginal.strip().replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        String limpio = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (limpio.isBlank()) {
            return "foto";
        }
        return limpio.toLowerCase(Locale.ROOT);
    }

    public static void exigirSegmentosValidos(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey vacío");
        }
        for (String parte : objectKey.split("/")) {
            if (parte.isBlank() || !SEGMENTO.matcher(parte).matches()) {
                throw new IllegalArgumentException("objectKey con segmento inválido");
            }
        }
    }
}