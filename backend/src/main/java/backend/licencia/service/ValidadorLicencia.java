package backend.licencia.service;

import backend.licencia.api.dto.ContratoLicencia;
import backend.licencia.model.ErrorLicencia;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class ValidadorLicencia {
    static final String MSG_NUMERO =
            "El número de licencia debe contener una letra seguida de 8 números, sin guiones. Ejemplo: Q12345678.";
    static final String MSG_VENCIMIENTO_RANGO =
            "La fecha de vencimiento debe estar entre 6 meses y 10 años después de la emisión.";
    static final String MSG_EMISION_FUTURA = "La fecha de emisión no puede ser posterior a hoy";
    static final String MSG_EMISION_ANTIGUA =
            "La fecha de emisión no puede ser anterior al año actual menos 10 años.";
    static final String MSG_VENCIMIENTO_PASADA_ALTA =
            "No puedes registrar una licencia con fecha de vencimiento pasada";
    public static final String MSG_LICENCIA_DUPLICADA =
            "Ya registraste una licencia con estos mismos datos. Corrige número o fechas.";

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final long MAX_IMAGEN = 5L * 1024 * 1024;
    private static final long MAX_PDF = 10L * 1024 * 1024;
    private static final Set<String> CATEGORIAS = Set.of("A-I", "A-IIa");

    private ValidadorLicencia() { }

    public static void exigirRegistro(ContratoLicencia.RegistrarRequest req) {
        if (req == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Solicitud vacía.");
        }
        if (req.idOperacion() == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "idOperacion es obligatorio.", Set.of("idOperacion"));
        }
        categoria(req.categoria());
        numero(req.numero());
        // EA12: permitir licencia ya vencida si el rango 6m–10a y emisión ≤ hoy se cumplen.
        fechas(req.fechaEmision(), req.fechaVencimiento(), false, true);
        fotoObligatoria("fotoFrontal", req.fotoFrontal());
        fotoObligatoria("fotoPosterior", req.fotoPosterior());
    }

    public static void exigirEditar(ContratoLicencia.EditarRequest req) {
        if (req == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Solicitud vacía.");
        }
        categoria(req.categoria());
        numero(req.numero());
        fechas(req.fechaEmision(), req.fechaVencimiento(), false, true);
        if (req.accionFotoFrontal() == null || req.accionFotoPosterior() == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Indica la acción de cada foto.",
                    Set.of("accionFotoFrontal", "accionFotoPosterior"));
        }
        if (req.accionFotoFrontal() == ContratoLicencia.AccionFoto.REEMPLAZAR && req.fotoFrontal() == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Adjunta la foto frontal de reemplazo.", Set.of("fotoFrontal"));
        }
        if (req.accionFotoPosterior() == ContratoLicencia.AccionFoto.REEMPLAZAR && req.fotoPosterior() == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Adjunta la foto posterior de reemplazo.", Set.of("fotoPosterior"));
        }
        if (req.accionFotoFrontal() == ContratoLicencia.AccionFoto.MANTENER && req.fotoFrontal() != null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "MANTENER no admite foto frontal nueva.", Set.of("fotoFrontal"));
        }
        if (req.accionFotoPosterior() == ContratoLicencia.AccionFoto.MANTENER && req.fotoPosterior() != null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "MANTENER no admite foto posterior nueva.", Set.of("fotoPosterior"));
        }
    }

    public static void exigirRenovar(ContratoLicencia.RenovarRequest req) {
        if (req == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Solicitud vacía.");
        }
        if (req.idOperacion() == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "idOperacion es obligatorio.", Set.of("idOperacion"));
        }
        if (req.idLicenciaAnterior() <= 0) {
            throw new ErrorLicencia("LICENCIA_NO_ENCONTRADA", "Licencia anterior no válida.");
        }
        categoria(req.categoria());
        numero(req.numero());
        fechas(req.fechaEmision(), req.fechaVencimiento(), true, true);
        fotoOpcional(req.fotoFrontal());
        fotoOpcional(req.fotoPosterior());
    }

    public static void exigirVencimientoRenovacion(LocalDate vencimientoNuevo, LocalDate vencimientoAnterior) {
        if (vencimientoNuevo == null || vencimientoAnterior == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Fechas de renovación incompletas.");
        }
        if (!vencimientoNuevo.isAfter(vencimientoAnterior)) {
            throw new ErrorLicencia("DATOS_INVALIDOS",
                    "La nueva fecha de vencimiento debe ser posterior a la anterior.",
                    Set.of("fechaVencimiento"));
        }
    }

    /** Solo se puede renovar desde la fecha de vencimiento (hoy Lima >= vencimiento). */
    public static void exigirPuedeRenovar(LocalDate vencimientoActual, LocalDate hoyLima) {
        if (vencimientoActual == null || hoyLima == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "No se pudo validar la renovación.");
        }
        if (hoyLima.isBefore(vencimientoActual)) {
            throw new ErrorLicencia(
                    "RENOVACION_NO_DISPONIBLE",
                    "Aún no puedes renovar: la licencia vence el "
                            + vencimientoActual
                            + ". Inténtalo a partir de esa fecha.");
        }
    }

    public static void exigirPresignUpload(String contentType, int tamanoBytes, String nombreOriginal) {
        if (contentType == null || contentType.isBlank()) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "contentType es obligatorio.", Set.of("contentType"));
        }
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "nombreOriginal es obligatorio.", Set.of("nombreOriginal"));
        }
        String tipo = contentType.toLowerCase(Locale.ROOT);
        boolean esPdf = tipo.equals("application/pdf");
        boolean esImagen = tipo.equals("image/jpeg") || tipo.equals("image/png") || tipo.equals("image/jpg");
        if (!esPdf && !esImagen) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Solo JPG, PNG o PDF.", Set.of("contentType"));
        }
        long max = esPdf ? MAX_PDF : MAX_IMAGEN;
        if (tamanoBytes <= 0 || tamanoBytes > max) {
            throw new ErrorLicencia(
                    "FOTO_DEMASIADO_GRANDE",
                    esPdf ? "El PDF supera el tamaño permitido (10 MB)."
                            : "La imagen supera el tamaño permitido (5 MB).");
        }
    }

    public static String normalizarNumero(String valor) {
        if (valor == null) {
            return null;
        }
        return valor.strip().toUpperCase(Locale.ROOT);
    }

    public static String estadoVigencia(LocalDate vencimiento, LocalDate hoy) {
        if (hoy.isAfter(vencimiento)) {
            return "VENCIDA";
        }
        return "VIGENTE";
    }

    private static void categoria(String valor) {
        if (valor == null || valor.isBlank() || !CATEGORIAS.contains(valor.strip())) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Selecciona categoría A-I o A-IIa.", Set.of("categoria"));
        }
    }

    private static void numero(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "numero obligatorio.", Set.of("numero"));
        }
        String limpio = normalizarNumero(valor);
        if (!limpio.matches("^[A-Z][0-9]{8}$")) {
            throw new ErrorLicencia("DATOS_INVALIDOS", MSG_NUMERO, Set.of("numero"));
        }
    }

  /**
   * @param exigirVencimientoFuturo alta/renovación: rechaza vencimiento &lt; hoy Lima
   * @param exigirEmisionNoFutura emisión &lt;= hoy Lima
   */
    private static void fechas(LocalDate emision, LocalDate vencimiento,
            boolean exigirVencimientoFuturo, boolean exigirEmisionNoFutura) {
        if (emision == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "fechaEmision obligatoria.", Set.of("fechaEmision"));
        }
        if (vencimiento == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "fechaVencimiento obligatoria.", Set.of("fechaVencimiento"));
        }
        if (!vencimiento.isAfter(emision)) {
            throw new ErrorLicencia("DATOS_INVALIDOS",
                    "La fecha de vencimiento debe ser posterior a la emisión.",
                    Set.of("fechaVencimiento"));
        }
        LocalDate min = emision.plusMonths(6);
        LocalDate max = emision.plusYears(10);
        if (vencimiento.isBefore(min) || vencimiento.isAfter(max)) {
            throw new ErrorLicencia("DATOS_INVALIDOS", MSG_VENCIMIENTO_RANGO, Set.of("fechaVencimiento"));
        }
        LocalDate hoy = LocalDate.now(LIMA);
        if (exigirEmisionNoFutura && emision.isAfter(hoy)) {
            throw new ErrorLicencia("DATOS_INVALIDOS", MSG_EMISION_FUTURA, Set.of("fechaEmision"));
        }
        LocalDate emisionMinima = LocalDate.of(hoy.getYear() - 10, 1, 1);
        if (emision.isBefore(emisionMinima)) {
            throw new ErrorLicencia("DATOS_INVALIDOS", MSG_EMISION_ANTIGUA, Set.of("fechaEmision"));
        }
        if (exigirVencimientoFuturo && vencimiento.isBefore(hoy)) {
            throw new ErrorLicencia("DATOS_INVALIDOS", MSG_VENCIMIENTO_PASADA_ALTA, Set.of("fechaVencimiento"));
        }
    }

    /** Evita reemitir exactamente la misma licencia (número + categoría + fechas). */
    public static void exigirNoDuplicadaExacta(
            String categoria,
            String numero,
            LocalDate emision,
            LocalDate vencimiento,
            String categoriaAnt,
            String numeroAnt,
            LocalDate emisionAnt,
            LocalDate vencimientoAnt) {
        if (categoriaAnt == null || numeroAnt == null || emisionAnt == null || vencimientoAnt == null) {
            return;
        }
        boolean igual = Objects.equals(categoria, categoriaAnt)
                && Objects.equals(normalizarNumero(numero), normalizarNumero(numeroAnt))
                && Objects.equals(emision, emisionAnt)
                && Objects.equals(vencimiento, vencimientoAnt);
        if (igual) {
            throw new ErrorLicencia("LICENCIA_DUPLICADA", MSG_LICENCIA_DUPLICADA,
                    Set.of("numero", "fechaEmision", "fechaVencimiento"));
        }
    }

    private static void fotoObligatoria(String campo, ContratoLicencia.FotoDto foto) {
        if (foto == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", campo + " obligatoria.", Set.of(campo));
        }
        validarFotoMetadata(foto, campo);
    }

    private static void fotoOpcional(ContratoLicencia.FotoDto foto) {
        if (foto == null) {
            return;
        }
        validarFotoMetadata(foto, "foto");
    }

    private static void validarFotoMetadata(ContratoLicencia.FotoDto foto, String campo) {
        if (foto.objectKey() == null || foto.objectKey().isBlank()) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "objectKey obligatorio.", Set.of(campo + ".objectKey"));
        }
        if (foto.contentType() == null || foto.tamanoBytes() == null || foto.nombreOriginal() == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Foto incompleta.", Set.of(campo));
        }
        exigirPresignUpload(foto.contentType(), foto.tamanoBytes(), foto.nombreOriginal());
    }
}