package backend.soat.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class FirmaAlertaSoat {
    private FirmaAlertaSoat() { }

    public static String calcular(long idSoat) {
        String canon = "SOAT\u001f" + idSoat;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canon.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular firma SOAT", ex);
        }
    }
}