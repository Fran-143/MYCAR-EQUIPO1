package backend.fotos;

import java.util.UUID;
import java.util.regex.Pattern;

/** Claves opacas: perfil/{idUsuario}/{uuid}.ext y vehiculos/{idUsuario}/{idVehiculo}/{uuid}.ext */
public final class RutasFotoPersonalizada {
    private static final Pattern SEGMENTO = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private RutasFotoPersonalizada() { }

    public static String clavePerfil(long idUsuario, String extension) {
        return "perfil/" + idUsuario + "/" + UUID.randomUUID() + "." + normalizarExt(extension);
    }

    public static String claveVehiculo(long idUsuario, long idVehiculo, String extension) {
        return "vehiculos/" + idUsuario + "/" + idVehiculo + "/" + UUID.randomUUID() + "."
                + normalizarExt(extension);
    }

    public static boolean esClavePerfilDe(String objectKey, long idUsuario) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }
        return objectKey.startsWith("perfil/" + idUsuario + "/");
    }

    public static boolean esClaveVehiculoDe(String objectKey, long idUsuario, long idVehiculo) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }
        return objectKey.startsWith("vehiculos/" + idUsuario + "/" + idVehiculo + "/");
    }

    public static void exigirClaveValida(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey vacío");
        }
        for (String parte : objectKey.split("/")) {
            if (parte.isBlank() || !SEGMENTO.matcher(parte).matches()) {
                throw new IllegalArgumentException("objectKey con segmento inválido");
            }
        }
    }

    private static String normalizarExt(String extension) {
        if (extension == null) {
            return "jpg";
        }
        String e = extension.strip().toLowerCase();
        if ("jpeg".equals(e)) {
            return "jpg";
        }
        if ("png".equals(e) || "jpg".equals(e)) {
            return e;
        }
        return "jpg";
    }
}
