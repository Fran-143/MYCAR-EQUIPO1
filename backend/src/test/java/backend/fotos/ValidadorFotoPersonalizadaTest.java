package backend.fotos;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorFotoPersonalizadaTest {

    @Test
    void aceptaJpegReal() throws Exception {
        byte[] jpeg = imagen("jpg");
        var ok = ValidadorFotoPersonalizada.validarYLimpiar(jpeg, "image/jpeg");
        assertEquals("image/jpeg", ok.contentType());
        assertTrue(ok.bytesSinExif().length > 0);
        assertEquals("image/jpeg", ValidadorFotoPersonalizada.detectarMime(ok.bytesSinExif()));
    }

    @Test
    void aceptaPngReal() throws Exception {
        byte[] png = imagen("png");
        var ok = ValidadorFotoPersonalizada.validarYLimpiar(png, "image/png");
        assertEquals("image/png", ok.contentType());
    }

    @Test
    void rechazaGif() {
        byte[] gif = new byte[]{'G', 'I', 'F', '8', '9', 'a', 0, 0};
        assertThrows(FotoException.class, () -> ValidadorFotoPersonalizada.validarYLimpiar(gif, "image/gif"));
    }

    @Test
    void rechazaWebp() {
        byte[] webp = new byte[]{
                'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0, 0, 0, 0
        };
        assertThrows(FotoException.class, () -> ValidadorFotoPersonalizada.validarYLimpiar(webp, "image/webp"));
    }

    @Test
    void rechazaPdf() {
        byte[] pdf = "%PDF-1.4 fake".getBytes();
        assertThrows(FotoException.class, () -> ValidadorFotoPersonalizada.validarYLimpiar(pdf, "application/pdf"));
    }

    @Test
    void rechazaExtensionFalsaPngComoJpeg() throws Exception {
        byte[] png = imagen("png");
        assertThrows(FotoException.class, () -> ValidadorFotoPersonalizada.validarYLimpiar(png, "image/jpeg"));
    }

    @Test
    void rechazaVacio() {
        assertThrows(FotoException.class, () -> ValidadorFotoPersonalizada.validarYLimpiar(new byte[0], "image/jpeg"));
    }

    @Test
    void rechazaMayorA5MiB() throws Exception {
        byte[] jpeg = imagen("jpg");
        byte[] grande = new byte[(int) ValidadorFotoPersonalizada.MAX_BYTES + 10];
        System.arraycopy(jpeg, 0, grande, 0, jpeg.length);
        // Relleno rompe decodificación; también supera tamaño
        assertThrows(FotoException.class, () -> ValidadorFotoPersonalizada.validarYLimpiar(grande, "image/jpeg"));
    }

    @Test
    void rechazaMalformadoConFirmaJpeg() {
        byte[] malo = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        assertThrows(FotoException.class, () -> ValidadorFotoPersonalizada.validarYLimpiar(malo, "image/jpeg"));
    }

    private static byte[] imagen(String formato) throws Exception {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 32, 32);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, formato, out));
        return out.toByteArray();
    }
}
