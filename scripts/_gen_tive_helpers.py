from pathlib import Path
root = Path(r"C:\Proyectos\MyCarEquipo1-talleres-apk")
files = {}
files["backend/src/main/java/backend/documentos/RutasDocumentoTive.java"] = """package backend.documentos;

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
        String base = nombreOriginal.strip().replace('\\\\', '/');
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
"""
files["backend/src/main/java/backend/documentos/RutasDocumentoSoporte.java"] = """package backend.documentos;

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

    public static boolean perteneceAUsuarioVehiculo(String objectKey, long idUsuario, long idVehiculo) {
        if (objectKey == null || objectKey.isBlank()) return false;
        return objectKey.startsWith("soporte/" + idUsuario + "/" + idVehiculo + "/");
    }

    public static String nombreSeguro(String nombreOriginal) {
        if (nombreOriginal == null || nombreOriginal.isBlank()) return "evidencia";
        String base = nombreOriginal.strip().replace('\\\\', '/');
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
"""
files["backend/src/main/java/backend/tive/model/ErrorTive.java"] = """package backend.tive.model;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ErrorTive extends RuntimeException {
    private final String codigo;
    private final Set<String> campos;
    private final boolean incierto;

    public ErrorTive(String codigo, String mensaje) {
        this(codigo, mensaje, Set.of(), false);
    }

    public ErrorTive(String codigo, String mensaje, Set<String> campos) {
        this(codigo, mensaje, campos, false);
    }

    public ErrorTive(String codigo, String mensaje, boolean incierto) {
        this(codigo, mensaje, Set.of(), incierto);
    }

    public ErrorTive(String codigo, String mensaje, Set<String> campos, boolean incierto) {
        super(mensaje);
        this.codigo = codigo;
        this.campos = campos;
        this.incierto = incierto;
    }

    public String codigo() { return codigo; }

    public Map<String, String> campos() {
        if (campos.isEmpty()) return Map.of();
        String mensaje = getMessage() == null ? "Revisa este campo." : getMessage();
        return campos.stream().collect(Collectors.toMap(k -> k, k -> mensaje));
    }

    public boolean incierto() { return incierto; }
}
"""
files["backend/src/main/java/backend/soporte/model/ErrorSoporte.java"] = """package backend.soporte.model;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ErrorSoporte extends RuntimeException {
    private final String codigo;
    private final Set<String> campos;
    private final boolean incierto;

    public ErrorSoporte(String codigo, String mensaje) {
        this(codigo, mensaje, Set.of(), false);
    }

    public ErrorSoporte(String codigo, String mensaje, Set<String> campos) {
        this(codigo, mensaje, campos, false);
    }

    public ErrorSoporte(String codigo, String mensaje, boolean incierto) {
        this(codigo, mensaje, Set.of(), incierto);
    }

    public ErrorSoporte(String codigo, String mensaje, Set<String> campos, boolean incierto) {
        super(mensaje);
        this.codigo = codigo;
        this.campos = campos;
        this.incierto = incierto;
    }

    public String codigo() { return codigo; }

    public Map<String, String> campos() {
        if (campos.isEmpty()) return Map.of();
        String mensaje = getMessage() == null ? "Revisa este campo." : getMessage();
        return campos.stream().collect(Collectors.toMap(k -> k, k -> mensaje));
    }

    public boolean incierto() { return incierto; }
}
"""

for rel, content in files.items():
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    print("wrote", rel)
