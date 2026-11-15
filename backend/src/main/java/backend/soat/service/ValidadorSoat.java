package backend.soat.service;

import backend.soat.api.dto.ContratoSoat;
import backend.soat.model.ErrorSoat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

public final class ValidadorSoat {
    private static final int MAX_ASEGURADORA = 120;
    /** Alineado a mycar.soat.numero_poliza VARCHAR(80). */
    private static final int MAX_POLIZA = 80;
    private static final int MAX_CONTRATANTE = 160;
    private static final long MAX_IMAGEN = 5L * 1024 * 1024;
    private static final long MAX_PDF = 10L * 1024 * 1024;
    private static final BigDecimal MAX_PRIMA = new BigDecimal("999999.00");
    static final String MSG_VIGENCIA_ANUAL =
            "El SOAT ordinario debe tener vigencia anual (aniversario calendario)";
    static final String MSG_INICIO_FUTURO =
            "La fecha de inicio no puede ser posterior a hoy";
    static final String MSG_POLIZA =
            "Ingresa un número de póliza o certificado válido (letras, números y separadores)";

    private ValidadorSoat() { }

    public static void exigirRegistro(ContratoSoat.RegistrarRequest req) {
        if (req == null) throw new ErrorSoat("DATOS_INVALIDOS", "Solicitud vacía.");
        if (req.idOperacion() == null) throw new ErrorSoat("DATOS_INVALIDOS", "idOperacion es obligatorio.",
                Set.of("idOperacion"));
        texto("aseguradora", req.aseguradora(), MAX_ASEGURADORA, true, true);
        poliza(req.numeroPoliza(), req.aseguradora());
        fechas(req.fechaInicio(), req.fechaVencimiento(), req.fechaEmision(), true, true);
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
        texto("aseguradora", req.aseguradora(), MAX_ASEGURADORA, true, true);
        poliza(req.numeroPoliza(), req.aseguradora());
        // Inicio futuro estricto se valida en exigirFechasRenovacion (permite día siguiente al vencimiento).
        fechas(req.fechaInicio(), req.fechaVencimiento(), req.fechaEmision(), true, false);
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
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        // Máximo hoy; solo se tolera el día siguiente al vencimiento anterior (renovar el mismo día).
        if (inicioNueva.isAfter(hoy) && !inicioNueva.equals(vencAnterior.plusDays(1))) {
            throw new ErrorSoat("DATOS_INVALIDOS", MSG_INICIO_FUTURO, Set.of("fechaInicio"));
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
        // Corregir admite catálogo histórico (HDI/Chubb/…) ya persistido.
        texto("aseguradora", req.aseguradora(), MAX_ASEGURADORA, true, false);
        poliza(req.numeroPoliza(), req.aseguradora());
        // CORREGIR: no rechazar vencimientos pasados; sí rechazar inicio futuro.
        fechas(req.fechaInicio(), req.fechaVencimiento(), req.fechaEmision(), false, true);
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

    /**
     * @param exigirVencimientoFuturo REGISTRO/RENOVACIÓN: rechaza vencimiento &lt; hoy Lima.
     *                                CORREGIR: false para permitir editar históricos ya vencidos.
     * @param exigirInicioNoFuturo REGISTRO/CORREGIR: rechaza inicio &gt; hoy Lima.
     *                             RENOVACIÓN: false (se valida en {@link #exigirFechasRenovacion}).
     */
    private static void fechas(LocalDate inicio, LocalDate vencimiento, LocalDate emision,
            boolean exigirVencimientoFuturo, boolean exigirInicioNoFuturo) {
        if (inicio == null) throw new ErrorSoat("DATOS_INVALIDOS", "fechaInicio obligatoria.", Set.of("fechaInicio"));
        if (vencimiento == null) {
            throw new ErrorSoat("DATOS_INVALIDOS", "fechaVencimiento obligatoria.", Set.of("fechaVencimiento"));
        }
        if (!vencimiento.isAfter(inicio)) {
            throw new ErrorSoat("DATOS_INVALIDOS",
                    "La fecha de vencimiento debe ser posterior a la fecha de inicio",
                    Set.of("fechaVencimiento"));
        }
        if (!esVigenciaAnualOrdinaria(inicio, vencimiento)) {
            throw new ErrorSoat("DATOS_INVALIDOS", MSG_VIGENCIA_ANUAL, Set.of("fechaVencimiento"));
        }
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        if (exigirInicioNoFuturo && inicio.isAfter(hoy)) {
            throw new ErrorSoat("DATOS_INVALIDOS", MSG_INICIO_FUTURO, Set.of("fechaInicio"));
        }
        if (exigirVencimientoFuturo && vencimiento.isBefore(hoy)) {
            throw new ErrorSoat("DATOS_INVALIDOS",
                    "No puedes registrar un SOAT con fecha de vencimiento pasada",
                    Set.of("fechaVencimiento"));
        }
        if (emision != null && emision.isAfter(vencimiento)) {
            throw new ErrorSoat("DATOS_INVALIDOS", "fechaEmision no puede ser posterior al vencimiento.",
                    Set.of("fechaEmision"));
        }
    }

    /**
     * SOAT ordinario: aniversario calendario ({@link LocalDate#plusYears(long)}), no 365 días.
     * Acepta fin = inicio+1 año o el día anterior (último día incluido del certificado).
     */
    public static boolean esVigenciaAnualOrdinaria(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null || !fin.isAfter(inicio)) return false;
        LocalDate aniversario = inicio.plusYears(1);
        LocalDate ultimoIncluido = aniversario.minusDays(1);
        return fin.equals(aniversario) || fin.equals(ultimoIncluido);
    }

    static String normalizarAseguradora(String valor) {
        return CatalogoAseguradorasSoat.codigoParaGuardar(valor);
    }

    static String normalizarPoliza(String valor) {
        if (valor == null) return null;
        return valor.strip().toUpperCase(Locale.ROOT);
    }

    private static void texto(String campo, String valor, int max, boolean obligatorio, boolean soloActivas) {
        if (valor == null || valor.isBlank()) {
            if (obligatorio) throw new ErrorSoat("DATOS_INVALIDOS", campo + " obligatorio.", Set.of(campo));
            return;
        }
        String limpio = valor.strip();
        if (limpio.isEmpty() || limpio.codePointCount(0, limpio.length()) > max) {
            throw new ErrorSoat("DATOS_INVALIDOS", campo + " inválido.", Set.of(campo));
        }
        if ("aseguradora".equals(campo)) {
            boolean ok = soloActivas
                    ? CatalogoAseguradorasSoat.esActiva(limpio)
                    : CatalogoAseguradorasSoat.esReconocida(limpio);
            if (!ok) {
                throw new ErrorSoat("DATOS_INVALIDOS", "Selecciona una aseguradora del catálogo", Set.of(campo));
            }
        }
    }

    private static void textoOpcional(String campo, String valor, int max) {
        if (valor == null || valor.isBlank()) return;
        texto(campo, valor, max, false, false);
    }

    private static void poliza(String valor, String aseguradora) {
        if (valor == null || valor.isBlank()) {
            throw new ErrorSoat("DATOS_INVALIDOS", "numeroPoliza obligatorio.", Set.of("numeroPoliza"));
        }
        String limpio = valor.strip();
        if (limpio.isEmpty() || limpio.codePointCount(0, limpio.length()) > MAX_POLIZA) {
            throw new ErrorSoat("DATOS_INVALIDOS", "numeroPoliza inválido.", Set.of("numeroPoliza"));
        }
        for (int i = 0; i < limpio.length(); ) {
            int cp = limpio.codePointAt(i);
            if (Character.isISOControl(cp)) {
                throw new ErrorSoat("DATOS_INVALIDOS", MSG_POLIZA, Set.of("numeroPoliza"));
            }
            i += Character.charCount(cp);
        }
        if (!limpio.matches("^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9][A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9\\-/. ]*$")) {
            throw new ErrorSoat("DATOS_INVALIDOS", MSG_POLIZA, Set.of("numeroPoliza"));
        }
        int alfanum = 0;
        for (int i = 0; i < limpio.length(); ) {
            int cp = limpio.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) alfanum++;
            i += Character.charCount(cp);
        }
        if (alfanum < 1) {
            throw new ErrorSoat("DATOS_INVALIDOS", MSG_POLIZA, Set.of("numeroPoliza"));
        }
        var perfil = CatalogoAseguradorasSoat.perfil(aseguradora);
        if (perfil != null) {
            if (perfil.longitudMinDocumentada() != null && alfanum < perfil.longitudMinDocumentada()) {
                throw new ErrorSoat("DATOS_INVALIDOS", MSG_POLIZA, Set.of("numeroPoliza"));
            }
            if (perfil.longitudMaxDocumentada() != null && alfanum > perfil.longitudMaxDocumentada()) {
                throw new ErrorSoat("DATOS_INVALIDOS", MSG_POLIZA, Set.of("numeroPoliza"));
            }
        }
    }

    private static void prima(BigDecimal valor) {
        if (valor == null) return;
        if (valor.signum() < 0 || valor.compareTo(MAX_PRIMA) > 0 || valor.scale() > 2) {
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