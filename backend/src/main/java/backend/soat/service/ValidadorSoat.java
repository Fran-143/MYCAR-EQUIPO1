package backend.soat.service;

import backend.soat.api.dto.ContratoSoat;
import backend.soat.model.ErrorSoat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

public final class ValidadorSoat {
    private static final int MAX_ASEGURADORA = 120;
    private static final int MAX_POLIZA = 15;
    private static final int DIGITOS_POLIZA = 7;
    private static final int MAX_CONTRATANTE = 160;
    private static final long MAX_IMAGEN = 5L * 1024 * 1024;
    private static final long MAX_PDF = 10L * 1024 * 1024;
    private static final BigDecimal MAX_PRIMA = new BigDecimal("999999.00");

    private ValidadorSoat() { }

    public static void exigirRegistro(ContratoSoat.RegistrarRequest req) {
        if (req == null) throw new ErrorSoat("DATOS_INVALIDOS", "Solicitud vacía.");
        if (req.idOperacion() == null) throw new ErrorSoat("DATOS_INVALIDOS", "idOperacion es obligatorio.",
                Set.of("idOperacion"));
        texto("aseguradora", req.aseguradora(), MAX_ASEGURADORA, true);
        texto("numeroPoliza", req.numeroPoliza(), MAX_POLIZA, true);
        fechas(req.fechaInicio(), req.fechaVencimiento(), req.fechaEmision());
        textoOpcional("nombreContratante", req.nombreContratante(), MAX_CONTRATANTE);
        prima(req.importePrima());
        documentoOpcional(req.documento());
    }

    public static void exigirRenovar(ContratoSoat.RenovarRequest req) {
        if (req == null) throw new ErrorSoat("DATOS_INVALIDOS", "Solicitud vacía.");
        if (req.idOperacion() == null) throw new ErrorSoat("DATOS_INVALIDOS", "idOperacion es obligatorio.",
                Set.of("idOperacion"));
        if (req.idSoatAnterior() <= 0) {
            throw new ErrorSoat("SOAT_NO_ENCONTRADO", "SOAT anterior no válido.");
        }
        texto("aseguradora", req.aseguradora(), MAX_ASEGURADORA, true);
        texto("numeroPoliza", req.numeroPoliza(), MAX_POLIZA, true);
        fechas(req.fechaInicio(), req.fechaVencimiento(), req.fechaEmision());
        textoOpcional("nombreContratante", req.nombreContratante(), MAX_CONTRATANTE);
        prima(req.importePrima());
        documentoOpcional(req.documento());
    }

    /**
     * RF31-EA01 / EA04: la nueva vigencia empieza después de la actual y vence después de ella.
     */
    public static void exigirFechasRenovacion(LocalDate inicioNueva, LocalDate vencNueva,
            LocalDate inicioAnterior, LocalDate vencAnterior) {
        if (inicioNueva == null || vencNueva == null || inicioAnterior == null || vencAnterior == null) {
            throw new ErrorSoat("DATOS_INVALIDOS", "Fechas de renovación incompletas.");
        }
        if (!inicioNueva.isAfter(vencAnterior)) {
            throw new ErrorSoat("SOAT_SOLAPE",
                    "La nueva vigencia no puede empezar antes de que termine la actual.",
                    Set.of("fechaInicio"));
        }
        if (!vencNueva.isAfter(vencAnterior)) {
            throw new ErrorSoat("DATOS_INVALIDOS",
                    "La nueva vigencia debe vencer después de la actual.",
                    Set.of("fechaVencimiento"));
        }
    }

    /** Renovar solo si ya se llegó al día de vencimiento del SOAT actual (hoy Lima >= vencimiento). */
    public static void exigirPuedeRenovar(LocalDate vencimientoAnterior, LocalDate hoyLima) {
        if (vencimientoAnterior == null || hoyLima == null) {
            throw new ErrorSoat("DATOS_INVALIDOS", "Fecha de vencimiento no disponible.");
        }
        if (hoyLima.isBefore(vencimientoAnterior)) {
            throw new ErrorSoat("SOAT_NO_RENOVABLE",
                    "Solo puedes renovar el SOAT a partir de su fecha de vencimiento");
        }
    }

    public static void exigirCorregir(ContratoSoat.CorregirRequest req) {
        if (req == null) throw new ErrorSoat("DATOS_INVALIDOS", "Solicitud vacía.");
        if (req.accionDocumento() == null) {
            throw new ErrorSoat("DATOS_INVALIDOS", "Indica la acción del documento.", Set.of("accionDocumento"));
        }
        texto("aseguradora", req.aseguradora(), MAX_ASEGURADORA, true);
        texto("numeroPoliza", req.numeroPoliza(), MAX_POLIZA, true);
        // CORREGIR: no rechazar vencimientos pasados de SOAT ya registrados.
        fechas(req.fechaInicio(), req.fechaVencimiento(), req.fechaEmision(), false);
        textoOpcional("nombreContratante", req.nombreContratante(), MAX_CONTRATANTE);
        prima(req.importePrima());
        if (req.accionDocumento() == ContratoSoat.AccionDocumento.REEMPLAZAR) {
            if (req.documento() == null) {
                throw new ErrorSoat("DATOS_INVALIDOS", "Adjunta el documento de reemplazo.", Set.of("documento"));
            }
            documentoOpcional(req.documento());
        } else if (req.accionDocumento() == ContratoSoat.AccionDocumento.MANTENER && req.documento() != null) {
            throw new ErrorSoat("DATOS_INVALIDOS", "MANTENER no admite documento nuevo.", Set.of("documento"));
        }
    }

    public static void exigirPresign(ContratoSoat.PresignUploadRequest req) {
        if (req == null || req.contentType() == null || req.contentType().isBlank()) {
            throw new ErrorSoat("DATOS_INVALIDOS", "contentType es obligatorio.", Set.of("contentType"));
        }
        if (req.tamanoBytes() == null || req.tamanoBytes() <= 0) {
            throw new ErrorSoat("DATOS_INVALIDOS", "tamanoBytes inválido.", Set.of("tamanoBytes"));
        }
        if (req.nombreOriginal() == null || req.nombreOriginal().isBlank()) {
            throw new ErrorSoat("DATOS_INVALIDOS", "nombreOriginal es obligatorio.", Set.of("nombreOriginal"));
        }
        long max = maximoPermitido(req.contentType());
        if (req.tamanoBytes() > max) {
            throw new ErrorSoat("DOCUMENTO_DEMASIADO_GRANDE", "El archivo supera el tamaño permitido.");
        }
        String tipo = req.contentType().toLowerCase(Locale.ROOT);
        if (!tipo.equals("application/pdf") && !tipo.startsWith("image/")) {
            throw new ErrorSoat("DATOS_INVALIDOS", "Solo JPG, PNG o PDF.", Set.of("contentType"));
        }
    }

    public static long maximoPermitido(String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).equals("application/pdf")) {
            return MAX_PDF;
        }
        return MAX_IMAGEN;
    }

    private static void fechas(LocalDate inicio, LocalDate vencimiento, LocalDate emision) {
        fechas(inicio, vencimiento, emision, true);
    }

    /**
     * @param exigirVencimientoFuturo REGISTRO/RENOVACIÓN: rechaza vencimiento &lt; hoy Lima.
     *                                CORREGIR: false para permitir editar históricos ya vencidos.
     */
    private static void fechas(LocalDate inicio, LocalDate vencimiento, LocalDate emision,
            boolean exigirVencimientoFuturo) {
        if (inicio == null) throw new ErrorSoat("DATOS_INVALIDOS", "fechaInicio obligatoria.", Set.of("fechaInicio"));
        if (vencimiento == null) {
            throw new ErrorSoat("DATOS_INVALIDOS", "fechaVencimiento obligatoria.", Set.of("fechaVencimiento"));
        }
        if (!vencimiento.isAfter(inicio)) {
            throw new ErrorSoat("DATOS_INVALIDOS",
                    "La fecha de vencimiento debe ser posterior a la fecha de inicio",
                    Set.of("fechaVencimiento"));
        }
        if (exigirVencimientoFuturo) {
            LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
            if (vencimiento.isBefore(hoy)) {
                throw new ErrorSoat("DATOS_INVALIDOS",
                        "No puedes registrar un SOAT con fecha de vencimiento pasada",
                        Set.of("fechaVencimiento"));
            }
        }
        if (emision != null && emision.isAfter(vencimiento)) {
            throw new ErrorSoat("DATOS_INVALIDOS", "fechaEmision no puede ser posterior al vencimiento.",
                    Set.of("fechaEmision"));
        }
    }

    private static final Set<String> ASEGURADORAS = Set.of(
            "RIMAC SEGUROS",
            "PACIFICO SEGUROS",
            "MAPFRE PERU",
            "LA POSITIVA SEGUROS",
            "INTERSEGURO",
            "PROTECTA SECURITY",
            "QUALITAS",
            "HDI SEGUROS",
            "CRECER SEGUROS",
            "CHUBB PERU",
            "BNP PARIBAS CARDIF"
    );

    static String normalizarAseguradora(String valor) {
        if (valor == null) return null;
        String n = java.text.Normalizer.normalize(valor.strip(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        return ASEGURADORAS.contains(n) ? n : null;
    }

    static String normalizarPoliza(String valor) {
        if (valor == null) return null;
        return valor.strip();
    }

    private static void texto(String campo, String valor, int max, boolean obligatorio) {
        if (valor == null || valor.isBlank()) {
            if (obligatorio) throw new ErrorSoat("DATOS_INVALIDOS", campo + " obligatorio.", Set.of(campo));
            return;
        }
        String limpio = valor.strip();
        if (limpio.isEmpty() || limpio.codePointCount(0, limpio.length()) > max) {
            throw new ErrorSoat("DATOS_INVALIDOS", campo + " inválido.", Set.of(campo));
        }
        if ("aseguradora".equals(campo)) {
            if (normalizarAseguradora(limpio) == null) {
                throw new ErrorSoat("DATOS_INVALIDOS", "Selecciona una aseguradora del catálogo", Set.of(campo));
            }
        }
        if ("numeroPoliza".equals(campo)) {
            long digitos = limpio.chars().filter(Character::isDigit).count();
            if (!limpio.matches("^[0-9]+(-[0-9]+)*$") || digitos != DIGITOS_POLIZA) {
                throw new ErrorSoat("DATOS_INVALIDOS",
                        "El número de póliza debe tener exactamente 7 dígitos", Set.of(campo));
            }
        }
    }

    private static void textoOpcional(String campo, String valor, int max) {
        if (valor == null || valor.isBlank()) return;
        texto(campo, valor, max, false);
    }

    private static void prima(BigDecimal valor) {
        if (valor == null) return;
        if (valor.signum() < 0 || valor.compareTo(MAX_PRIMA) > 0 || valor.stripTrailingZeros().scale() > 2) {
            throw new ErrorSoat("DATOS_INVALIDOS", "El importe de la prima no es válido", Set.of("importePrima"));
        }
    }

    private static void documentoOpcional(ContratoSoat.DocumentoDto doc) {
        if (doc == null) return;
        if (doc.objectKey() == null || doc.objectKey().isBlank()) {
            throw new ErrorSoat("DATOS_INVALIDOS", "objectKey obligatorio.", Set.of("documento.objectKey"));
        }
        if (doc.contentType() == null || doc.tamanoBytes() == null || doc.nombreOriginal() == null) {
            throw new ErrorSoat("DATOS_INVALIDOS", "Documento incompleto.", Set.of("documento"));
        }
        long max = maximoPermitido(doc.contentType());
        if (doc.tamanoBytes() <= 0 || doc.tamanoBytes() > max) {
            throw new ErrorSoat("DOCUMENTO_DEMASIADO_GRANDE", "El documento supera el tamaño permitido.");
        }
    }

    public static String estadoCobertura(LocalDate inicio, LocalDate vencimiento, LocalDate hoy) {
        if (hoy.isBefore(inicio)) return "POR_INICIAR";
        if (!hoy.isAfter(vencimiento)) return "VIGENTE";
        return "VENCIDA";
    }
}