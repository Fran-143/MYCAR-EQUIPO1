package backend.soat.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Catálogo SOAT Perú alineado a APESEG + perfiles de validación.
 * Sin longitudes/regex inventadas por compañía (SBS: cada aseguradora define su identificador).
 */
public final class CatalogoAseguradorasSoat {

    public record PerfilValidacionPoliza(
            String id,
            String nombreVisible,
            String mensajeAyuda,
            String ejemploIlustrativo,
            String fuente,
            String fechaVerificacion,
            Integer longitudMinDocumentada,
            Integer longitudMaxDocumentada
    ) { }

    public record Entrada(
            String id,
            String codigo,
            String nombre,
            Set<String> alias,
            boolean activo,
            PerfilValidacionPoliza perfil
    ) { }

    private static final Map<String, Entrada> POR_CLAVE = new LinkedHashMap<>();

    static {
        registrar(activa("RIMAC", "RIMAC SEGUROS", "RÍMAC SEGUROS",
                Set.of("RIMAC", "RIMAC SEGUROS Y REASEGUROS"),
                "Números y separadores según tu certificado. MyCar valida el formato, no la autenticidad oficial.",
                null, "APESEG; SBS", "2025-11-15"));
        registrar(activa("POSITIVA", "LA POSITIVA SEGUROS", "LA POSITIVA SEGUROS",
                Set.of("LA POSITIVA", "POSITIVA", "POSITIVA SEGUROS"),
                "Se admiten números y guiones; conserva ceros iniciales. Sin longitud fija documentada.",
                null, "APESEG; SBS", "2025-11-15"));
        registrar(activa("PACIFICO", "PACIFICO SEGUROS", "PACÍFICO SEGUROS",
                Set.of("PACIFICO", "PACIFICO SEGUROS Y REASEGUROS"),
                "Ingresa el identificador de tu certificado Pacífico. Sin longitud fija obligatoria.",
                null, "APESEG; Pacífico; SBS", "2025-11-15"));
        registrar(activa("MAPFRE", "MAPFRE PERU", "MAPFRE PERÚ",
                Set.of("MAPFRE", "MAPFRE PERU"),
                "Sin formato obligatorio confirmado. Validación general de MyCar.",
                null, "APESEG; SBS", "2025-11-15"));
        registrar(activa("INTERSEGURO", "INTERSEGURO", "INTERSEGURO",
                Set.of("INTERSEGURO COMPANIA DE SEGUROS"),
                "Ejemplo web de 8 dígitos es ilustrativo, no obligatorio. Usa el número de tu certificado.",
                "12345678", "APESEG; Interseguro (ejemplo ilustrativo); SBS", "2025-11-15"));
        registrar(activa("PROTECTA", "PROTECTA SECURITY", "PROTECTA SECURITY",
                Set.of("PROTECTA", "PROTECTA SEGUROS"),
                "Formato obligatorio no confirmado. Validación general.",
                null, "APESEG; SBS", "2025-11-15"));
        registrar(activa("CRECER", "CRECER SEGUROS", "CRECER SEGUROS",
                Set.of("CRECER"),
                "Figura en APESEG; sin longitud fija documentada. Validación general.",
                null, "APESEG; SBS estadísticas 2025", "2025-11-15"));
        registrar(activa("VIVIR", "VIVIR SEGUROS", "VIVIR SEGUROS",
                Set.of("VIVIR", "VIVIR SEGUROS S.A."),
                "Formato obligatorio no confirmado. Validación general.",
                null, "APESEG; SBS", "2025-11-15"));
        registrar(activa("QUALITAS", "QUALITAS PERU", "QUÁLITAS PERÚ",
                Set.of("QUALITAS", "QUALITAS PERU", "QUALITAS COMPANIA DE SEGUROS"),
                "Portal distingue póliza/certificado; sin longitud fija inferida.",
                null, "APESEG; Quálitas; SBS", "2025-11-15"));
        // Históricos (consulta/corrección)
        registrar(historica("HDI", "HDI SEGUROS", "HDI SEGUROS", Set.of("HDI")));
        registrar(historica("CHUBB", "CHUBB PERU", "CHUBB PERÚ", Set.of("CHUBB")));
        registrar(historica("CARDIF", "BNP PARIBAS CARDIF", "BNP PARIBAS CARDIF",
                Set.of("CARDIF", "BNP PARIBAS CARDIF")));
    }

    private CatalogoAseguradorasSoat() { }

    private static Entrada activa(String id, String codigo, String nombre, Set<String> alias,
            String ayuda, String ejemplo, String fuente, String fecha) {
        return new Entrada(id, codigo, nombre, alias, true,
                new PerfilValidacionPoliza(id, nombre, ayuda, ejemplo, fuente, fecha, null, null));
    }

    private static Entrada historica(String id, String codigo, String nombre, Set<String> alias) {
        return new Entrada(id, codigo, nombre, alias, false,
                new PerfilValidacionPoliza(id, nombre, "Registro histórico. Validación general.",
                        null, "Histórico MyCar", "2025-11-15", null, null));
    }

    private static void registrar(Entrada e) {
        indexar(e.codigo(), e);
        indexar(e.nombre(), e);
        indexar(e.id(), e);
        for (String a : e.alias()) {
            indexar(a, e);
        }
    }

    private static void indexar(String clave, Entrada e) {
        String n = normalizar(clave);
        if (n != null) {
            POR_CLAVE.putIfAbsent(n, e);
        }
    }

    public static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String n = java.text.Normalizer.normalize(texto.strip(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        return n;
    }

    public static Entrada resolver(String codigoONombre) {
        String n = normalizar(codigoONombre);
        return n == null ? null : POR_CLAVE.get(n);
    }

    public static boolean esActiva(String codigoONombre) {
        Entrada e = resolver(codigoONombre);
        return e != null && e.activo();
    }

    public static boolean esReconocida(String codigoONombre) {
        return resolver(codigoONombre) != null;
    }

    /** Código canónico para persistir (sin tildes). */
    public static String codigoParaGuardar(String codigoONombre) {
        Entrada e = resolver(codigoONombre);
        return e == null ? null : e.codigo();
    }

    public static PerfilValidacionPoliza perfil(String codigoONombre) {
        Entrada e = resolver(codigoONombre);
        return e == null ? null : e.perfil();
    }
}
