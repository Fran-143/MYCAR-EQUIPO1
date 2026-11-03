package backend.alertas.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;

/** SHA-256 canónico para índice de equivalencia activa (diccionario MVP). */
public final class FirmaEquivalenciaAlerta {
    private static final String FORMATO = "1";

    private FirmaEquivalenciaAlerta() { }

    public static String calcular(
            long idTipoMantenimiento,
            String criterio,
            LocalDate fechaObjetivo,
            Integer kmObjetivo,
            String servicioOtroNormalizado
    ) {
        String canon = FORMATO + '\u001f'
                + idTipoMantenimiento + '\u001f'
                + criterio + '\u001f'
                + (fechaObjetivo == null ? "\u0000" : fechaObjetivo) + '\u001f'
                + (kmObjetivo == null ? "\u0000" : kmObjetivo) + '\u001f'
                + (servicioOtroNormalizado == null ? "" : servicioOtroNormalizado);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canon.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular firma_equivalencia", ex);
        }
    }
}
