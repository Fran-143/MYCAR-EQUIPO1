package backend.alertas.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Huella SHA-256 (64 hex) para operacion_idempotente.hash_solicitud de ANULAR_ALERTA. */
final class HuellaAnularAlerta {
    private HuellaAnularAlerta() { }

    static String calcular(long vehiculo, long idAlerta, String motivo, UUID idOperacion) {
        String canon = "RF24_ANULAR_ALERTA_V1\u001f" + vehiculo + '\u001f' + idAlerta + '\u001f'
                + normalizar(motivo) + '\u001f' + idOperacion;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canon.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular hash_solicitud de ANULAR_ALERTA", ex);
        }
    }

    static String normalizar(String motivo) {
        return motivo == null ? "" : motivo.trim().replaceAll("\\s+", " ");
    }
}
