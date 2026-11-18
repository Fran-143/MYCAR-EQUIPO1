package backend.tive.service;

import backend.tive.api.dto.ContratoTive;
import backend.tive.model.ErrorTive;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ValidadorTive {
    static final Pattern VIN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");
    static final Pattern MOTOR = Pattern.compile("^[A-Z0-9 -]{5,15}$");
    static final Pattern SERIE = Pattern.compile("^[A-Z0-9]{6,80}$");
    static final Pattern PLACA = Pattern.compile("^(?:[A-Z][0-9][A-Z][0-9]{3}|[A-Z]{3}[0-9]{3})$");
    static final Pattern TITULO = Pattern.compile("^[A-Z0-9-]{1,32}$");
    static final Pattern CODIGO = Pattern.compile("^[A-Z0-9]{4,32}$");
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> MIME = Set.of(
            "image/jpeg", "image/png", "application/pdf");

    private ValidadorTive() { }

    public static void exigirRegistro(ContratoTive.RegistrarRequest req) {
        if (req == null) throw new ErrorTive("DATOS_INVALIDOS", "Solicitud vacía.");
        if (req.idOperacion() == null) {
            throw new ErrorTive("DATOS_INVALIDOS", "idOperacion es obligatorio.", Set.of("idOperacion"));
        }
        documentales(req.numeroTitulo(), req.codigoVerificacion(), req.fechaTitulo(),
                req.zonaRegistral(), req.sedeRegistral(), req.numeroMotor(), req.numeroSerie());
        documentoOpcional(req.documento());
    }

    public static void exigirActualizar(ContratoTive.ActualizarRequest req) {
        if (req == null) throw new ErrorTive("DATOS_INVALIDOS", "Solicitud vacía.");
        if (req.accionDocumento() == null) {
            throw new ErrorTive("DATOS_INVALIDOS", "Indica la acción del documento.", Set.of("accionDocumento"));
        }
        documentales(req.numeroTitulo(), req.codigoVerificacion(), req.fechaTitulo(),
                req.zonaRegistral(), req.sedeRegistral(), req.numeroMotor(), req.numeroSerie());
        if (req.accionDocumento() == ContratoTive.AccionDocumento.AGREGAR
                || req.accionDocumento() == ContratoTive.AccionDocumento.REEMPLAZAR) {
            if (req.documento() == null) {
                throw new ErrorTive("DATOS_INVALIDOS", "Adjunta el documento.", Set.of("documento"));
            }
            documentoOpcional(req.documento());
        }
    }

    public static String normalizarVin(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ErrorTive("VIN_AUSENTE", "Ingresa el número de VIN", Set.of("numeroVin"));
        }
        if (raw.chars().anyMatch(Character::isWhitespace)) {
            throw new ErrorTive("VIN_INVALIDO", "El VIN no puede contener espacios", Set.of("numeroVin"));
        }
        String vin = raw.strip().toUpperCase(Locale.ROOT);
        if (vin.chars().anyMatch(c -> c == 'I' || c == 'O' || c == 'Q')) {
            throw new ErrorTive("VIN_INVALIDO", "El VIN no permite las letras I, O ni Q", Set.of("numeroVin"));
        }
        if (vin.length() < 17) {
            throw new ErrorTive("VIN_INVALIDO",
                    "El VIN debe tener 17 caracteres. Actualmente tienes " + vin.length() + " de 17",
                    Set.of("numeroVin"));
        }
        if (vin.length() > 17) {
            throw new ErrorTive("VIN_INVALIDO", "El VIN no puede superar los 17 caracteres", Set.of("numeroVin"));
        }
        if (!vin.chars().allMatch(c -> (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) {
            throw new ErrorTive("VIN_INVALIDO", "El VIN solo permite letras y números", Set.of("numeroVin"));
        }
        if (!VIN.matcher(vin).matches()) {
            throw new ErrorTive("VIN_INVALIDO", "El VIN solo permite letras y números", Set.of("numeroVin"));
        }
        return vin;
    }

    public static String normalizarMotor(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ErrorTive("DATOS_INVALIDOS", "Ingresa el número de motor", Set.of("numeroMotor"));
        }
        String v = raw.strip().toUpperCase(Locale.ROOT);
        if (v.length() < 5 || v.length() > 15) {
            throw new ErrorTive("DATOS_INVALIDOS",
                    "El número de motor debe tener entre 5 y 15 caracteres", Set.of("numeroMotor"));
        }
        if (!MOTOR.matcher(v).matches()) {
            throw new ErrorTive("DATOS_INVALIDOS",
                    "El número de motor contiene caracteres no permitidos", Set.of("numeroMotor"));
        }
        return v;
    }

    public static String normalizarSerie(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ErrorTive("DATOS_INVALIDOS", "Ingresa el número de serie", Set.of("numeroSerie"));
        }
        String v = raw.strip().toUpperCase(Locale.ROOT);
        if (v.length() < 6) {
            throw new ErrorTive("DATOS_INVALIDOS",
                    "El número de serie debe tener al menos 6 caracteres", Set.of("numeroSerie"));
        }
        if (!SERIE.matcher(v).matches()) {
            throw new ErrorTive("DATOS_INVALIDOS",
                    "El número de serie solo permite letras y números", Set.of("numeroSerie"));
        }
        return v;
    }

    public static String normalizarPlaca(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ErrorTive("DATOS_INVALIDOS", "La placa del vehículo es obligatoria.", Set.of("placa"));
        }
        String placa = raw.strip().toUpperCase(Locale.ROOT);
        if (!PLACA.matcher(placa).matches()) {
            throw new ErrorTive("DATOS_INVALIDOS", "Placa inválida.", Set.of("placa"));
        }
        return placa;
    }

    public static String normalizarTitulo(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ErrorTive("DATOS_INVALIDOS", "Ingresa el número de título", Set.of("numeroTitulo"));
        }
        String v = raw.strip().toUpperCase(Locale.ROOT);
        if (v.length() > 32) {
            throw new ErrorTive("DATOS_INVALIDOS",
                    "El número de título no puede superar los 32 caracteres", Set.of("numeroTitulo"));
        }
        if (!TITULO.matcher(v).matches()) {
            throw new ErrorTive("DATOS_INVALIDOS",
                    "El número de título contiene caracteres no permitidos", Set.of("numeroTitulo"));
        }
        return v;
    }

    public static String normalizarCodigo(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ErrorTive("DATOS_INVALIDOS", "Ingresa el código de verificación", Set.of("codigoVerificacion"));
        }
        if (raw.chars().anyMatch(Character::isWhitespace)) {
            throw new ErrorTive("DATOS_INVALIDOS",
                    "El código de verificación no permite espacios", Set.of("codigoVerificacion"));
        }
        String v = raw.strip().toUpperCase(Locale.ROOT);
        if (v.length() < 4) {
            throw new ErrorTive("DATOS_INVALIDOS",
                    "El código debe tener al menos 4 caracteres", Set.of("codigoVerificacion"));
        }
        if (v.length() > 32) {
            throw new ErrorTive("DATOS_INVALIDOS",
                    "El código no puede superar los 32 caracteres", Set.of("codigoVerificacion"));
        }
        if (!CODIGO.matcher(v).matches()) {
            throw new ErrorTive("DATOS_INVALIDOS",
                    "El código solo permite letras y números", Set.of("codigoVerificacion"));
        }
        return v;
    }

    public static void exigirMimeYTamano(String contentType, long bytes) {
        if (contentType == null || !MIME.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ErrorTive("DATOS_INVALIDOS", "El archivo no tiene un formato permitido", Set.of("documento"));
        }
        if (bytes <= 0 || bytes > MAX_BYTES) {
            throw new ErrorTive("DOCUMENTO_DEMASIADO_GRANDE",
                    "El archivo supera el tamaño máximo de 10 MB", Set.of("documento"));
        }
    }

    public static long maximoPermitido(String contentType) {
        exigirMimeYTamano(contentType == null ? "" : contentType, 1);
        return MAX_BYTES;
    }

    private static void documentales(String titulo, String codigo, LocalDate fecha,
            String zona, String sede, String motor, String serie) {
        normalizarTitulo(titulo);
        normalizarCodigo(codigo);
        if (fecha == null) {
            throw new ErrorTive("DATOS_INVALIDOS", "Selecciona la fecha del título", Set.of("fechaTitulo"));
        }
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        if (fecha.isAfter(hoy)) {
            throw new ErrorTive("DATOS_INVALIDOS",
                    "La fecha no puede ser posterior a la fecha actual", Set.of("fechaTitulo"));
        }
        boolean zonaVacia = zona == null || zona.isBlank();
        boolean sedeVacia = sede == null || sede.isBlank();
        if (zonaVacia != sedeVacia) {
            throw new ErrorTive("DATOS_INVALIDOS",
                    "Zona y sede registral deben informarse juntas",
                    Set.of("zonaRegistral", "sedeRegistral"));
        }
        if (!zonaVacia) {
            textoRegistral("zonaRegistral", zona);
            textoRegistral("sedeRegistral", sede);
        }
        normalizarMotor(motor);
        normalizarSerie(serie);
    }

    private static void documentoOpcional(ContratoTive.DocumentoDto doc) {
        if (doc == null) return;
        if (doc.objectKey() == null || doc.objectKey().isBlank()
                || doc.contentType() == null || doc.nombreOriginal() == null
                || doc.tamanoBytes() == null) {
            throw new ErrorTive("DATOS_INVALIDOS", "Metadatos de documento incompletos.", Set.of("documento"));
        }
        exigirMimeYTamano(doc.contentType(), doc.tamanoBytes());
    }

    private static String textoRegistral(String campo, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ErrorTive("DATOS_INVALIDOS", "Campo obligatorio.", Set.of(campo));
        }
        String v = raw.strip().toUpperCase(Locale.ROOT);
        if (v.length() > 80) {
            throw new ErrorTive("DATOS_INVALIDOS", "Texto demasiado largo.", Set.of(campo));
        }
        return v;
    }
}
