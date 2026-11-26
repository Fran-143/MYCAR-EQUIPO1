package backend.soporte.service;

import backend.soporte.model.ErrorSoporte;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ValidadorIncidenciaGeneral {
    private static final Set<String> CATEGORIAS = Set.of(
            "FALLAS_APP",
            "CUENTA_ACCESO",
            "VEHICULOS_DOCUMENTOS",
            "ALERTAS_NOTIFICACIONES",
            "SERVICIOS_EXTERNOS",
            "OTROS"
    );
    private static final Pattern SOLO_ESPACIOS = Pattern.compile("^\\s+$");
    private static final Pattern HINT_CORRECCION_VIN = Pattern.compile(
            "(?i)(corregir|cambiar|actualizar|modificar)\\s+(el\\s+)?vin\\b|\\bvin\\s+(incorrecto|errado|mal)");

    private ValidadorIncidenciaGeneral() { }

    public static String categoria(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ErrorSoporte("DATOS_INVALIDOS", "Selecciona el tema de la incidencia", Set.of("categoria"));
        }
        String cat = raw.strip().toUpperCase(Locale.ROOT);
        if (!CATEGORIAS.contains(cat)) {
            throw new ErrorSoporte("DATOS_INVALIDOS", "Tema de incidencia no válido", Set.of("categoria"));
        }
        return cat;
    }

    public static String temaOtro(String categoria, String raw) {
        if (!"OTROS".equals(categoria)) {
            return null;
        }
        if (raw == null || raw.isBlank() || SOLO_ESPACIOS.matcher(raw).matches()) {
            throw new ErrorSoporte("DATOS_INVALIDOS", "Especifica el tema", Set.of("temaOtro"));
        }
        String tema = normalizarEspacios(raw);
        if (tema.length() < 3 || tema.length() > 120) {
            throw new ErrorSoporte("DATOS_INVALIDOS",
                    "El tema personalizado debe tener entre 3 y 120 caracteres", Set.of("temaOtro"));
        }
        return tema;
    }

    public static String asunto(String raw) {
        if (raw == null || raw.isBlank() || SOLO_ESPACIOS.matcher(raw).matches()) {
            throw new ErrorSoporte("DATOS_INVALIDOS", "Escribe un asunto", Set.of("asunto"));
        }
        String asunto = normalizarEspacios(raw);
        if (asunto.length() < 5 || asunto.length() > 120) {
            throw new ErrorSoporte("DATOS_INVALIDOS",
                    "El asunto debe tener entre 5 y 120 caracteres", Set.of("asunto"));
        }
        return asunto;
    }

    public static String descripcion(String raw) {
        if (raw == null || raw.isBlank() || SOLO_ESPACIOS.matcher(raw).matches()) {
            throw new ErrorSoporte("DATOS_INVALIDOS", "Describe lo ocurrido", Set.of("descripcion"));
        }
        String desc = raw.strip().replaceAll("[ \\t]+", " ");
        String utiles = desc.replaceAll("\\s+", "");
        if (utiles.length() < 15) {
            throw new ErrorSoporte("DATOS_INVALIDOS",
                    "La descripción debe tener al menos 15 caracteres útiles", Set.of("descripcion"));
        }
        if (desc.length() > 3000) {
            throw new ErrorSoporte("DATOS_INVALIDOS",
                    "La descripción no puede superar los 3000 caracteres", Set.of("descripcion"));
        }
        return desc;
    }

    public static String respuestaAdmin(String raw) {
        if (raw == null || raw.isBlank() || SOLO_ESPACIOS.matcher(raw).matches()) {
            throw new ErrorSoporte("DATOS_INVALIDOS", "La respuesta al usuario es obligatoria", Set.of("respuesta"));
        }
        String r = raw.strip();
        if (r.length() < 5 || r.length() > 3000) {
            throw new ErrorSoporte("DATOS_INVALIDOS",
                    "La respuesta debe tener entre 5 y 3000 caracteres", Set.of("respuesta"));
        }
        return r;
    }

    public static String motivoRechazo(String raw) {
        if (raw == null || raw.isBlank() || SOLO_ESPACIOS.matcher(raw).matches()) {
            throw new ErrorSoporte("DATOS_INVALIDOS", "El motivo del rechazo es obligatorio", Set.of("motivo"));
        }
        String m = raw.strip();
        if (m.length() < 5 || m.length() > 500) {
            throw new ErrorSoporte("DATOS_INVALIDOS",
                    "El motivo debe tener entre 5 y 500 caracteres", Set.of("motivo"));
        }
        return m;
    }

    public static String observacionOpcional(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String o = raw.strip();
        if (o.length() > 500) {
            throw new ErrorSoporte("DATOS_INVALIDOS",
                    "La observación no puede superar los 500 caracteres", Set.of("observacion"));
        }
        return o;
    }

    /** Orientación soft: no bloquea el envío; el cliente puede mostrar guía a Corrección VIN. */
    public static boolean sugiereCorreccionVin(String asunto, String descripcion) {
        String texto = ((asunto == null ? "" : asunto) + " " + (descripcion == null ? "" : descripcion)).strip();
        return HINT_CORRECCION_VIN.matcher(texto).find();
    }

    private static String normalizarEspacios(String raw) {
        return raw.strip().replaceAll("\\s+", " ");
    }
}
