package backend.documentos;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Prefijo: soporte/{idUsuario}/{idVehiculo}/temp|solicitud/… */
public final class RutasDocumentoSoporte {
    private static final Pattern SEGMENTO = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private RutasDocumentoSoporte() { }

    public static String claveTemp(long idUsuario, long idVehiculo, String nombreSeguro) {
        return "soporte/" + idUsuario + "/" + idVehiculo + "/temp/" + UUID.randomUUID() + "_" + nombreSeguro;
    }

    /** Evidencia RF60 (sin vehículo asociado). */
    public static String claveTempIncidencia(long idUsuario, String nombreSeguro) {
        return "soporte/" + idUsuario + "/incidencia/temp/" + UUID.randomUUID() + "_" + nombreSeguro;
    }

    public static boolean perteneceAUsuarioVehiculo(String objectKey, long idUsuario, long idVehiculo) {
        if (objectKey == null || objectKey.isBlank()) return false;
        return objectKey.startsWith("soporte/" + idUsuario + "/" + idVehiculo + "/");
    }

    public static String nombreSeguro(String nombreOriginal) {
        if (nombreOriginal == null || nombreOriginal.isBlank()) return "evidencia";
        String base = nombreOriginal.strip().replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        if (base.length() > 120) base = base.substring(0, 120);
        String limpio = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        return limpio.isBlank() ? "evidencia" : limpio.toLowerCase(Locale.ROOT);
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
