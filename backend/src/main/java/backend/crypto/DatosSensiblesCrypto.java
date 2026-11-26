package backend.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM + HMAC-SHA256 (índice ciego). Claves externas; nonce aleatorio por operación.
 * Formato sellado: Base64URL( keyIdLen|keyId|nonce12|ciphertext+tag ).
 */
public final class DatosSensiblesCrypto {
    private static final int NONCE = 12;
    private static final int TAG_BITS = 128;

    private final byte[] aesKey;
    private final byte[] blindKey;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public DatosSensiblesCrypto(byte[] aesKey, byte[] blindKey, String keyId) {
        this.aesKey = exigir256(aesKey, "aes");
        this.blindKey = exigir256(blindKey, "blind");
        if (keyId == null || keyId.isBlank() || keyId.length() > 16) {
            throw new IllegalArgumentException("keyId inválido");
        }
        if (MessageDigest.isEqual(this.aesKey, this.blindKey)) {
            throw new IllegalArgumentException("AES y blind deben ser claves distintas");
        }
        this.keyId = keyId.trim();
    }

    public String keyId() {
        return keyId;
    }

    public String sellar(String plaintext, String aad) {
        if (plaintext == null) return null;
        byte[] nonce = new byte[NONCE];
        random.nextBytes(nonce);
        byte[] plain = plaintext.getBytes(StandardCharsets.UTF_8);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            if (aad != null) c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] cipher = c.doFinal(plain);
            byte[] kid = keyId.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(1 + kid.length + NONCE + cipher.length);
            buf.put((byte) kid.length);
            buf.put(kid);
            buf.put(nonce);
            buf.put(cipher);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buf.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cifrado no disponible", ex);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    public String abrir(String sellado, String aad) {
        if (sellado == null || sellado.isBlank()) return null;
        byte[] raw = Base64.getUrlDecoder().decode(sellado);
        ByteBuffer buf = ByteBuffer.wrap(raw);
        int kidLen = Byte.toUnsignedInt(buf.get());
        if (kidLen <= 0 || kidLen > 16 || buf.remaining() < kidLen + NONCE + 16) {
            throw new IllegalStateException("Paquete cifrado inválido");
        }
        byte[] kid = new byte[kidLen];
        buf.get(kid);
        String id = new String(kid, StandardCharsets.UTF_8);
        if (!keyId.equals(id)) {
            throw new IllegalStateException("Versión de clave no disponible: " + id);
        }
        byte[] nonce = new byte[NONCE];
        buf.get(nonce);
        byte[] cipher = new byte[buf.remaining()];
        buf.get(cipher);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            if (aad != null) c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] plain = c.doFinal(cipher);
            try {
                return new String(plain, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo descifrar", ex);
        }
    }

    public byte[] sellarBytes(byte[] plain, String aad) {
        if (plain == null) return null;
        byte[] nonce = new byte[NONCE];
        random.nextBytes(nonce);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            if (aad != null) c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] cipher = c.doFinal(plain);
            byte[] kid = keyId.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(1 + kid.length + NONCE + cipher.length);
            buf.put((byte) kid.length);
            buf.put(kid);
            buf.put(nonce);
            buf.put(cipher);
            return buf.array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cifrado no disponible", ex);
        }
    }

    public byte[] abrirBytes(byte[] sellado, String aad) {
        if (sellado == null || sellado.length < 1 + NONCE + 16) {
            throw new IllegalStateException("Paquete cifrado inválido");
        }
        ByteBuffer buf = ByteBuffer.wrap(sellado);
        int kidLen = Byte.toUnsignedInt(buf.get());
        if (kidLen <= 0 || kidLen > 16 || buf.remaining() < kidLen + NONCE + 16) {
            throw new IllegalStateException("Paquete cifrado inválido");
        }
        byte[] kid = new byte[kidLen];
        buf.get(kid);
        if (!keyId.equals(new String(kid, StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Versión de clave no disponible");
        }
        byte[] nonce = new byte[NONCE];
        buf.get(nonce);
        byte[] cipher = new byte[buf.remaining()];
        buf.get(cipher);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            if (aad != null) c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return c.doFinal(cipher);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo descifrar", ex);
        }
    }

    /** Índice ciego determinista (hex) para búsqueda de duplicados. */
    public String indiceCiego(String valorNormalizado) {
        if (valorNormalizado == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(blindKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(valorNormalizado.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC no disponible", ex);
        }
    }

    public static boolean pareceSellado(String valor) {
        if (valor == null || valor.length() < 20) return false;
        try {
            byte[] raw = Base64.getUrlDecoder().decode(valor);
            return raw.length > 1 + NONCE + 16;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] exigir256(byte[] k, String nombre) {
        if (k == null || k.length != 32) {
            throw new IllegalArgumentException("Clave " + nombre + " debe ser 256 bits");
        }
        return k.clone();
    }
}
