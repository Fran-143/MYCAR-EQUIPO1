package backend.documentos;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Prefijo canónico: tive/{idUsuario}/{idVehiculo}/{idTive|temp}/… */
public final class RutasDocumentoTive {
    private static final Pattern SEGMENTO = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private RutasDocumentoTive() { }

    public static String prefijoTemp(long idUsuario, long idVehiculo) {
        return "tive/" + idUsuario + "/" + idVehiculo + "/temp";
    }

    public static String claveTemp(long idUsuario, long idVehiculo, String nombreSeguro) {
        return prefijoTemp(idUsuario, idVehiculo) + "/" + UUID.randomUUID() + "_" + nombreSeguro;
    }

    public static boolean perteneceAVehiculo(String objectKey, long idUsuario, long idVehiculo) {
        if (objectKey == null || objectKey.isBlank()) return false;
        return objectKey.startsWith("tive/" + idUsuario + "/" + idVehiculo + "/");
    }

    public static String nombreSeguro(String nombreOriginal) {
        if (nombreOriginal == null || nombreOriginal.isBlank()) return "documento";
        String base = nombreOriginal.strip().replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        if (base.length() > 120) base = base.substring(0, 120);
        String limpio = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        return limpio.isBlank() ? "documento" : limpio.toLowerCase(Locale.ROOT);
    }

    public static void exigirSegmentosValidos(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("objectKey vacío");
        for (String parte : objectKey.split("/")) {
            if (parte.isBlank() || !SEGMENTO.matcher(parte).matches()) {
                throw new IllegalArgumentException("objectKey con segmento inválido");
            }
        }
    }
}
