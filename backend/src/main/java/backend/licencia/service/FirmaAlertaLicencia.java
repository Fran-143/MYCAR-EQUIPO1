package backend.licencia.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class FirmaAlertaLicencia {
    private FirmaAlertaLicencia() { }

    public static String calcular(long idLicencia) {
        String canon = "LICENCIA\u001f" + idLicencia;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canon.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular firma LICENCIA", ex);
        }
    }
}