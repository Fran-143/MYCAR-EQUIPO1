package backend.soat.service;

import backend.crypto.DatosSensiblesCrypto;
import backend.crypto.DatosSensiblesCryptoGate;
import backend.documentos.FakeAlmacenamientoDocumentos;
import backend.soat.model.ErrorSoat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Focalizado: sin claves no se persisten sensibles; gate fail-closed. */
class SoatAutorizacionDocumentoTest {

    @Test
    void sinCifradoNoPermiteSellar() {
        var gate = DatosSensiblesCryptoGate.deshabilitado();
        ErrorSoat e = assertThrows(ErrorSoat.class, () -> gate.sellarCampo("0124589", "aad"));
        assertEquals("CIFRADO_NO_CONFIGURADO", e.codigo());
    }

    @Test
    void fakeStorageGuardaYLee() {
        var fake = new FakeAlmacenamientoDocumentos();
        String key = "soat/1/2/temp/doc.pdf";
        fake.guardar(key, "hola".getBytes(), "application/pdf");
        assertTrue(fake.confirmarExistencia(key));
        assertArrayEquals("hola".getBytes(), fake.leer(key));
    }

    @Test
    void documentoCifradoRoundtrip() {
        byte[] aes = new byte[32];
        byte[] blind = new byte[32];
        for (int i = 0; i < 32; i++) {
            aes[i] = (byte) (i + 1);
            blind[i] = (byte) (i + 50);
        }
        var crypto = new DatosSensiblesCrypto(aes, blind, "v1");
        byte[] enc = crypto.sellarBytes("pdf".getBytes(), "soat|doc|1");
        assertArrayEquals("pdf".getBytes(), crypto.abrirBytes(enc, "soat|doc|1"));
    }
}
