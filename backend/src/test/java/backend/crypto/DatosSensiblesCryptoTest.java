package backend.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatosSensiblesCryptoTest {

    @Test
    void roundtripTextoYBytes() {
        // claves distintas de 32 bytes
        byte[] aes = new byte[32];
        byte[] blind = new byte[32];
        for (int i = 0; i < 32; i++) {
            aes[i] = (byte) i;
            blind[i] = (byte) (i + 40);
        }
        var crypto = new DatosSensiblesCrypto(aes, blind, "v1");
        String sealed = crypto.sellar("Q12345678", "soat|poliza|1");
        assertNotEquals("Q12345678", sealed);
        assertEquals("Q12345678", crypto.abrir(sealed, "soat|poliza|1"));
        assertThrows(IllegalStateException.class, () -> crypto.abrir(sealed, "otro-aad"));

        byte[] doc = "pdf-bytes".getBytes();
        byte[] enc = crypto.sellarBytes(doc, "soat|doc|1");
        assertArrayEquals(doc, crypto.abrirBytes(enc, "soat|doc|1"));

        String b1 = crypto.indiceCiego("0124589");
        String b2 = crypto.indiceCiego("0124589");
        assertEquals(b1, b2);
        assertNotEquals(b1, crypto.indiceCiego("9999999"));
    }

    @Test
    void gateFailClosed() {
        var gate = DatosSensiblesCryptoGate.deshabilitado();
        assertFalse(gate.habilitado());
        var err = assertThrows(backend.soat.model.ErrorSoat.class, () -> gate.sellarCampo("x", "a"));
        assertEquals("CIFRADO_NO_CONFIGURADO", err.codigo());
    }
}
