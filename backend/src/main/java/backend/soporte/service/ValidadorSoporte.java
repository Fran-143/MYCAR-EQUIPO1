package backend.soporte.service;

import backend.soporte.model.ErrorSoporte;
import backend.tive.service.ValidadorTive;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ValidadorSoporte {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_MOTIVO = 250;
    private static final Set<String> MIME = Set.of("image/jpeg", "image/png", "application/pdf");

    private ValidadorSoporte() { }

    public static String normalizarVinPropuesto(String raw, String vinActual) {
        String propuesto;
        try {
            propuesto = ValidadorTive.normalizarVin(raw);
        } catch (backend.tive.model.ErrorTive ex) {
            throw new ErrorSoporte(ex.codigo(), ex.getMessage(), ex.campos().keySet());
        }
        if (vinActual != null && propuesto.equals(vinActual.strip().toUpperCase(Locale.ROOT))) {
            throw new ErrorSoporte("VIN_SIN_CAMBIO",
                    "El VIN solicitado debe ser diferente del VIN actual",
                    Set.of("vinPropuesto"));
        }
        return propuesto;
    }

    public static String motivo(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ErrorSoporte("DATOS_INVALIDOS", "Describe el motivo de la corrección", Set.of("motivo"));
        }
        String m = raw.strip().toUpperCase(Locale.forLanguageTag("es-PE"));
        if (m.length() > MAX_MOTIVO) {
            throw new ErrorSoporte("DATOS_INVALIDOS",
                    "El motivo no puede superar los 250 caracteres", Set.of("motivo"));
        }
        return m;
    }

    /** Evidencia opcional: si no hay bytes, se omite; si hay, valida MIME y tamaño. */
    public static void validarEvidenciaOpcional(String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        if (contentType == null || !MIME.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ErrorSoporte("DATOS_INVALIDOS", "El archivo no tiene un formato permitido", Set.of("file"));
        }
        if (bytes.length > MAX_BYTES) {
            throw new ErrorSoporte("DOCUMENTO_DEMASIADO_GRANDE",
                    "El archivo supera el tamaño máximo de 10 MB", Set.of("file"));
        }
    }

    public static void exigirIdOperacion(UUID id) {
        if (id == null) {
            throw new ErrorSoporte("DATOS_INVALIDOS", "idOperacion es obligatorio.", Set.of("idOperacion"));
        }
    }
}
