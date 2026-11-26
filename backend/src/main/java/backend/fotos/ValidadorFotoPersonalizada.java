package backend.fotos;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Valida JPG/PNG reales (firma + decodificación), tamaño ≤5 MiB y dimensiones razonables.
 * Re-encode para eliminar EXIF/GPS antes de persistir.
 */
public final class ValidadorFotoPersonalizada {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_LADO_PX = 4096;
    private static final Set<String> MIME_OK = Set.of("image/jpeg", "image/png");

    public record FotoValidada(byte[] bytesSinExif, String contentType, String extension) { }

    private ValidadorFotoPersonalizada() { }

    public static FotoValidada validarYLimpiar(byte[] originales, String contentTypeDeclarado) {
        if (originales == null || originales.length == 0) {
            throw FotoException.validation("El archivo de imagen está vacío.");
        }
        if (originales.length > MAX_BYTES) {
            throw FotoException.validation("La imagen no puede superar 5 MiB.");
        }
        String detectado = detectarMime(originales);
        if (detectado == null) {
            throw FotoException.validation("Solo se admiten imágenes JPG/JPEG o PNG auténticas.");
        }
        if (contentTypeDeclarado != null && !contentTypeDeclarado.isBlank()) {
            String declarado = contentTypeDeclarado.strip().toLowerCase(Locale.ROOT);
            if (declarado.contains(";")) {
                declarado = declarado.substring(0, declarado.indexOf(';')).strip();
            }
            if (!MIME_OK.contains(declarado) && !"image/jpg".equals(declarado)) {
                throw FotoException.validation("Solo se admiten imágenes JPG/JPEG o PNG auténticas.");
            }
            if ("image/jpg".equals(declarado)) {
                declarado = "image/jpeg";
            }
            if (!declarado.equals(detectado)) {
                throw FotoException.validation("El tipo de imagen no coincide con el contenido real.");
            }
        }
        BufferedImage imagen;
        try {
            imagen = ImageIO.read(new ByteArrayInputStream(originales));
        } catch (IOException ex) {
            throw FotoException.validation("La imagen está dañada o no se puede leer.");
        }
        if (imagen == null) {
            throw FotoException.validation("La imagen está dañada o no se puede leer.");
        }
        if (imagen.getWidth() <= 0 || imagen.getHeight() <= 0
                || imagen.getWidth() > MAX_LADO_PX || imagen.getHeight() > MAX_LADO_PX) {
            throw FotoException.validation("Las dimensiones de la imagen no son válidas.");
        }
        String formato = "image/png".equals(detectado) ? "png" : "jpg";
        byte[] limpios = reEncode(imagen, formato);
        if (limpios.length == 0 || limpios.length > MAX_BYTES) {
            throw FotoException.validation("No se pudo procesar la imagen dentro del límite permitido.");
        }
        return new FotoValidada(limpios, detectado, formato.equals("png") ? "png" : "jpg");
    }

    static String detectarMime(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return "image/png";
        }
        // GIF
        if (bytes.length >= 4
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return null;
        }
        // WEBP (RIFF....WEBP)
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return null;
        }
        // PDF
        if (bytes.length >= 4
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
            return null;
        }
        return null;
    }

    private static byte[] reEncode(BufferedImage imagen, String formato) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage rgb = imagen;
            if ("jpg".equals(formato) && imagen.getType() != BufferedImage.TYPE_INT_RGB) {
                rgb = new BufferedImage(imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_INT_RGB);
                var g = rgb.createGraphics();
                g.drawImage(imagen, 0, 0, null);
                g.dispose();
            }
            if (!ImageIO.write(rgb, formato, out)) {
                throw FotoException.validation("No se pudo procesar la imagen.");
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw FotoException.validation("No se pudo procesar la imagen.");
        }
    }
}
