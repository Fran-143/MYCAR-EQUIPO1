package backend.crypto;

import backend.soat.model.ErrorSoat;

/** Fail-closed: sin claves no se permiten mutaciones que persistan datos sensibles. */
public final class DatosSensiblesCryptoGate {
    private final DatosSensiblesCrypto crypto;
    private final boolean enabled;

    private DatosSensiblesCryptoGate(DatosSensiblesCrypto crypto, boolean enabled) {
        this.crypto = crypto;
        this.enabled = enabled;
    }

    public static DatosSensiblesCryptoGate deshabilitado() {
        return new DatosSensiblesCryptoGate(null, false);
    }

    public static DatosSensiblesCryptoGate habilitado(DatosSensiblesCrypto crypto) {
        if (crypto == null) throw new IllegalArgumentException("crypto");
        return new DatosSensiblesCryptoGate(crypto, true);
    }

    public boolean habilitado() {
        return enabled;
    }

    public DatosSensiblesCrypto exigir() {
        if (!enabled || crypto == null) {
            throw new ErrorSoat(
                    "CIFRADO_NO_CONFIGURADO",
                    "El cifrado de datos sensibles no está configurado. No se guardan datos en texto plano.");
        }
        return crypto;
    }

    /** Lectura: si hay crypto, úsalo; si el valor parece legacy plano, devuélvelo. */
    public String abrirCampo(String valor, String aad) {
        if (valor == null) return null;
        if (!enabled || crypto == null) return valor;
        if (!DatosSensiblesCrypto.pareceSellado(valor)) return valor;
        return crypto.abrir(valor, aad);
    }

    public String sellarCampo(String plaintext, String aad) {
        return exigir().sellar(plaintext, aad);
    }

    public String indiceCiego(String normalizado) {
        return exigir().indiceCiego(normalizado);
    }

    public String keyId() {
        return exigir().keyId();
    }

    public byte[] sellarBytes(byte[] plain, String aad) {
        return exigir().sellarBytes(plain, aad);
    }

    public byte[] abrirBytes(byte[] sellado, String aad) {
        return exigir().abrirBytes(sellado, aad);
    }
}
