package backend.documentos;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FakeAlmacenamientoDocumentosTest {

    @Test
    void presignYConfirmacionTrasSubidaSimulada() {
        var fake = new FakeAlmacenamientoDocumentos();
        String key = "soat/1/2/temp/uuid_poliza.pdf";
        var upload = fake.generarPresignUpload(key, "application/pdf", 1024);
        assertNotNull(upload.url());
        assertFalse(fake.confirmarExistencia(key));
        fake.simularSubida(key);
        assertTrue(fake.confirmarExistencia(key));
        var download = fake.generarPresignDownload(key);
        assertTrue(download.url().contains("download"));
        fake.borrarLogicoHuerfano(key);
        assertFalse(fake.confirmarExistencia(key));
    }

    @Test
    void rutasSoatValidanTitularidad() {
        assertTrue(RutasDocumentoSoat.perteneceAVehiculo("soat/5/10/temp/x.pdf", 5, 10));
        assertFalse(RutasDocumentoSoat.perteneceAVehiculo("soat/5/11/temp/x.pdf", 5, 10));
    }
}