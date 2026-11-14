package backend.documentos;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Prefijo canónico: soat/{idUsuario}/{idVehiculo}/{idSoat|temp}/… */
public final class RutasDocumentoSoat {
    private static final Pattern SEGMENTO = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private RutasDocumentoSoat() { }

    public static String prefijoTemp(long idUsuario, long idVehiculo) {
        return "soat/" + idUsuario + "/" + idVehiculo + "/temp";
    }

    public static String prefijoSoat(long idUsuario, long idVehiculo, long idSoat) {
        return "soat/" + idUsuario + "/" + idVehiculo + "/" + idSoat;
    }

    public static String claveTemp(long idUsuario, long idVehiculo, String nombreSeguro) {
        return prefijoTemp(idUsuario, idVehiculo) + "/" + UUID.randomUUID() + "_" + nombreSeguro;
    }

    public static String claveSoat(long idUsuario, long idVehiculo, long idSoat, String nombreSeguro) {
        return prefijoSoat(idUsuario, idVehiculo, idSoat) + "/" + nombreSeguro;
    }

    public static boolean perteneceAVehiculo(String objectKey, long idUsuario, long idVehiculo) {
        if (objectKey == null || objectKey.isBlank()) return false;
        String esperado = "soat/" + idUsuario + "/" + idVehiculo + "/";
        return objectKey.startsWith(esperado);
    }

    public static String nombreSeguro(String nombreOriginal) {
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            return "documento";
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
            return "documento";
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