package backend.vehiculos.service;

import backend.fotos.FotoException;
import backend.fotos.FotoVehiculoService;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.DatosDetalleVehiculo;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * RF40: genera un PDF temporal con allowlist pública (marca, modelo, año, combustible).
 * No persiste el PDF ni expone placa/VIN/kilometraje/SOAT/TIVE ni datos del titular.
 */
@Service
public class CompartirVehiculoPdfService {
    private static final String DISCLAIMER =
            "Documento informativo generado por MyCar. No sustituye la documentación oficial del vehículo";
    private static final String FOTO_PREDETERMINADA = "mycar/vehiculo-foto-predeterminada.jpg";

    private final ConsultaRegistroVehiculoService consultas;
    private final FotoVehiculoService fotos;
    private final CompartirVehiculoGate gate;

    public CompartirVehiculoPdfService(
            ConsultaRegistroVehiculoService consultas,
            FotoVehiculoService fotos,
            CompartirVehiculoGate gate
    ) {
        this.consultas = consultas;
        this.fotos = fotos;
        this.gate = gate;
    }

    public record PdfGenerado(byte[] bytes, String nombreArchivo) { }

    @Transactional(readOnly = true)
    public PdfGenerado generar(long idUsuarioAutenticado, long idVehiculo, boolean incluirFoto) {
        DatosDetalleVehiculo detalle = consultas.consultarDetalle(idUsuarioAutenticado, idVehiculo);
        gate.exigirPermitido(idUsuarioAutenticado, idVehiculo, detalle.estadoVehiculo());

        String combustible = etiquetaCombustible(detalle);
        byte[] fotoBytes = null;
        if (incluirFoto) {
            // Evitar leerBytes si no hay foto: la excepción marcaría rollback-only
            // en la transacción de lectura y terminaría en ERROR_INTERNO (EA3/EA4).
            fotoBytes = resolverFoto(idUsuarioAutenticado, idVehiculo, detalle.tieneFoto());
        }

        try {
            byte[] pdf = construirPdf(detalle.marca(), detalle.modelo(), detalle.anioFabricacion(),
                    combustible, fotoBytes);
            if (pdf == null || pdf.length == 0) {
                throw new ErrorRegistroVehiculo("PDF_NO_GENERADO");
            }
            return new PdfGenerado(pdf, "MyCar-vehiculo.pdf");
        } catch (ErrorRegistroVehiculo ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ErrorRegistroVehiculo("PDF_NO_GENERADO");
        }
    }

    static String etiquetaCombustible(DatosDetalleVehiculo detalle) {
        if (detalle.motorElectrico()) {
            return "No aplica";
        }
        String c = detalle.combustible();
        if (c == null || c.isBlank()) {
            return "No aplica";
        }
        return c.trim();
    }

    private byte[] resolverFoto(long idUsuario, long idVehiculo, boolean tieneFoto) {
        if (!tieneFoto) {
            return cargarFotoPredeterminada();
        }
        try {
            return fotos.leerBytes(idUsuario, idVehiculo);
        } catch (FotoException ex) {
            if (ex.reason() == FotoException.Reason.NOT_FOUND) {
                return cargarFotoPredeterminada();
            }
            throw new ErrorRegistroVehiculo("PDF_NO_GENERADO");
        } catch (ErrorRegistroVehiculo ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ErrorRegistroVehiculo("PDF_NO_GENERADO");
        }
    }

    private static byte[] cargarFotoPredeterminada() {
        try (InputStream in = new ClassPathResource(FOTO_PREDETERMINADA).getInputStream()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                throw new ErrorRegistroVehiculo("PDF_NO_GENERADO");
            }
            return bytes;
        } catch (IOException ex) {
            throw new ErrorRegistroVehiculo("PDF_NO_GENERADO");
        }
    }

    private static byte[] construirPdf(
            String marca, String modelo, int anio, String combustible, byte[] fotoOpcional
    ) throws DocumentException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 42, 42, 48, 56);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setCompressionLevel(0);
        document.open();

        Color azul = new Color(0x00, 0x33, 0x66);
        Color azulClaro = new Color(0xE8, 0xF4, 0xFC);
        Color azulSuave = new Color(0xD6, 0xEA, 0xF8);
        Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, azul);
        Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, azul);
        Font subtitulo = FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(0x5A, 0x6B, 0x7D));
        Font nombreVehiculo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, azul);
        Font etiqueta = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(0x6B, 0x7C, 0x93));
        Font valor = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
        Font pie = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0x4A, 0x55, 0x68));

        dibujarDecoracion(writer, azulSuave, azulClaro);

        Paragraph myCar = new Paragraph("MyCar", brand);
        myCar.setAlignment(Element.ALIGN_CENTER);
        myCar.setSpacingAfter(10f);
        document.add(myCar);

        Paragraph tituloP = new Paragraph("Ficha pública del vehículo", titulo);
        tituloP.setAlignment(Element.ALIGN_CENTER);
        tituloP.setSpacingAfter(2f);
        document.add(tituloP);

        Paragraph sub = new Paragraph("Información general de uso público", subtitulo);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(fotoOpcional != null ? 14f : 18f);
        document.add(sub);

        if (fotoOpcional != null && fotoOpcional.length > 0) {
            Image imagen = Image.getInstance(fotoOpcional);
            float maxAncho = PageSize.A4.getWidth() - 120f;
            float maxAlto = 200f;
            imagen.scaleToFit(maxAncho, maxAlto);
            imagen.setAlignment(Element.ALIGN_CENTER);
            document.add(imagen);
            document.add(new Paragraph(" "));
        }

        Paragraph nombre = new Paragraph(safe(marca) + " " + safe(modelo), nombreVehiculo);
        nombre.setAlignment(Element.ALIGN_CENTER);
        nombre.setSpacingAfter(14f);
        document.add(nombre);

        PdfPTable filas = new PdfPTable(2);
        filas.setWidthPercentage(100f);
        filas.setWidths(new float[]{1.15f, 2f});
        agregarFila(filas, "Marca", safe(marca), etiqueta, valor);
        agregarFila(filas, "Modelo", safe(modelo), etiqueta, valor);
        agregarFila(filas, "Año", String.valueOf(anio), etiqueta, valor);
        agregarFila(filas, "Combustible", safe(combustible), etiqueta, valor);

        PdfPCell contenido = new PdfPCell();
        contenido.setBorder(Rectangle.NO_BORDER);
        contenido.setBackgroundColor(azulClaro);
        contenido.setPadding(8f);
        contenido.addElement(filas);

        PdfPTable wrap = new PdfPTable(1);
        wrap.setWidthPercentage(92f);
        PdfPCell borde = new PdfPCell(contenido);
        borde.setBackgroundColor(azulClaro);
        borde.setBorderColor(new Color(0x00, 0x33, 0x66));
        borde.setBorderWidth(1.2f);
        borde.setPadding(6f);
        wrap.addCell(borde);
        document.add(wrap);

        Paragraph disclaimer = new Paragraph(DISCLAIMER, pie);
        disclaimer.setSpacingBefore(28f);
        disclaimer.setAlignment(Element.ALIGN_CENTER);
        document.add(disclaimer);

        document.close();
        return out.toByteArray();
    }

    private static void dibujarDecoracion(PdfWriter writer, Color azulSuave, Color azulClaro) {
        PdfContentByte canvas = writer.getDirectContentUnder();
        float w = PageSize.A4.getWidth();
        float h = PageSize.A4.getHeight();
        canvas.setColorFill(azulClaro);
        canvas.circle(36, h - 36, 48);
        canvas.fill();
        canvas.setColorFill(azulSuave);
        canvas.circle(w - 28, 48, 70);
        canvas.fill();
        canvas.setColorFill(azulClaro);
        canvas.circle(w - 90, 20, 40);
        canvas.fill();
    }

    private static void agregarFila(PdfPTable tabla, String label, String value, Font fLabel, Font fValue) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, fLabel));
        PdfPCell c2 = new PdfPCell(new Phrase(value, fValue));
        c1.setBorderWidth(0);
        c2.setBorderWidth(0);
        c1.setBackgroundColor(new Color(0xE8, 0xF4, 0xFC));
        c2.setBackgroundColor(new Color(0xE8, 0xF4, 0xFC));
        c1.setPadding(8f);
        c2.setPadding(8f);
        tabla.addCell(c1);
        tabla.addCell(c2);
    }

    private static String safe(String s) {
        return s == null || s.isBlank() ? "—" : s.trim();
    }
}
