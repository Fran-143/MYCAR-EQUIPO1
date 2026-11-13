package backend.vehiculos.domain;

import java.util.Set;

/** Motivos admitidos por RF16 en cliente y servidor. */
public final class MotivoCorreccionKilometraje {
    public static final String ERROR_ACCIDENTAL = "Error accidental";
    public static final String ERROR_DIGITACION = "Error de digitación";
    public static final String OTRO_PREFIJO = "Otro: ";

    private static final Set<String> FIJOS = Set.of(ERROR_ACCIDENTAL, ERROR_DIGITACION);

    private MotivoCorreccionKilometraje() {
    }

    /**
     * @return motivo normalizado o null si es inválido (5–200 chars; fijo o "Otro: …").
     */
    public static String normalizar(String crudo) {
        if (crudo == null) {
            return null;
        }
        String motivo = crudo.trim().replaceAll("\\s+", " ");
        int largo = motivo.codePointCount(0, motivo.length());
        if (largo < 5 || largo > 200) {
            return null;
        }
        if (FIJOS.contains(motivo)) {
            return motivo;
        }
        if (motivo.startsWith(OTRO_PREFIJO)) {
            String detalle = motivo.substring(OTRO_PREFIJO.length()).trim();
            int detalleLargo = detalle.codePointCount(0, detalle.length());
            if (detalleLargo < 5 || detalleLargo > 180) {
                return null;
            }
            return OTRO_PREFIJO + detalle;
        }
        return null;
    }
}
