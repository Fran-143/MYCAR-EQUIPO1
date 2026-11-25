package backend.historial.export;

import backend.historial.export.ExportarHistorialModelos.*;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * RF26 — presentación visual del PDF (OpenPDF).
 * No altera datos ni reglas de exportación; solo tipografía, colores y layout.
 */
final class ExportarHistorialPdfGenerator {
    private static final Color NAVY = new Color(0x00, 0x33, 0x66);
    private static final Color AZUL = new Color(0x15, 0x65, 0xC0);
    private static final Color AZUL_SUAVE = new Color(0xE3, 0xF2, 0xFD);
    private static final Color GRIS_TARJETA = new Color(0xF7, 0xF9, 0xFC);
    private static final Color GRIS_BORDE = new Color(0xD0, 0xD7, 0xE2);
    private static final Color GRIS_TEXTO = new Color(0x45, 0x4F, 0x5B);
    private static final Color GRIS_MUTE = new Color(0x6B, 0x72, 0x80);
    private static final Color VERDE_BG = new Color(0xE8, 0xF5, 0xE9);
    private static final Color VERDE_TX = new Color(0x1B, 0x5E, 0x20);
    private static final Color ROJO_BG = new Color(0xFF, 0xEB, 0xEE);
    private static final Color ROJO_TX = new Color(0xB7, 0x1C, 0x1C);
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ExportarHistorialPdfGenerator() { }

    static byte[] generar(CabeceraExport cabecera, List<MantenimientoExport> registros) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 56, 52);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PiePagina());
            doc.open();

            Font fBrand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.WHITE);
            Font fTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.WHITE);
            Font fSeccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, NAVY);
            Font fLabel = FontFactory.getFont(FontFactory.HELVETICA, 8, GRIS_MUTE);
            Font fValor = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, NAVY);
            Font fNormal = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, GRIS_TEXTO);
            Font fNombre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, NAVY);
            Font fBadgeOk = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, VERDE_TX);
            Font fBadgeBad = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, ROJO_TX);
            Font fVacio = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, GRIS_MUTE);

            doc.add(cabeceraVisual(fBrand, fTitulo));
            doc.add(Chunk.NEWLINE);
            doc.add(tarjetaVehiculo(cabecera, fSeccion, fLabel, fValor));
            doc.add(Chunk.NEWLINE);

            List<MantenimientoExport> lista = registros == null ? List.of() : registros;
            doc.add(resumenVisual(lista, fSeccion, fLabel, fValor));
            doc.add(Chunk.NEWLINE);

            Paragraph hReg = new Paragraph("Mantenimientos", fSeccion);
            hReg.setSpacingAfter(8f);
            doc.add(hReg);

            if (lista.isEmpty()) {
                PdfPTable vacio = new PdfPTable(1);
                vacio.setWidthPercentage(100);
                PdfPCell c = new PdfPCell(new Phrase(
                        "Sin mantenimientos para los criterios elegidos.", fVacio));
                c.setBackgroundColor(GRIS_TARJETA);
                c.setBorderColor(GRIS_BORDE);
                c.setPadding(16f);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                vacio.addCell(c);
                doc.add(vacio);
            } else {
                int i = 1;
                for (MantenimientoExport m : lista) {
                    doc.add(bloqueRegistro(m, i++, fNombre, fLabel, fNormal, fBadgeOk, fBadgeBad));
                }
            }

            doc.close();
            return out.toByteArray();
        } catch (DocumentException ex) {
            throw new ExportarHistorialError("PDF_NO_GENERADO", "No se pudo generar el PDF.");
        }
    }

    private static PdfPTable cabeceraVisual(Font fBrand, Font fTitulo) throws DocumentException {
        PdfPTable outer = new PdfPTable(new float[]{1.1f, 4f});
        outer.setWidthPercentage(100);
        outer.setSpacingAfter(2f);

        PdfPCell logo = new PdfPCell();
        logo.setBackgroundColor(NAVY);
        logo.setBorder(Rectangle.NO_BORDER);
        logo.setPadding(12f);
        logo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logo.setHorizontalAlignment(Element.ALIGN_CENTER);
        logo.setPhrase(new Phrase("MC", fBrand));
        logo.setMinimumHeight(56f);
        outer.addCell(logo);

        PdfPCell texto = new PdfPCell();
        texto.setBackgroundColor(AZUL);
        texto.setBorder(Rectangle.NO_BORDER);
        texto.setPadding(12f);
        texto.setPaddingLeft(14f);
        texto.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph p1 = new Paragraph("MyCar", fBrand);
        Paragraph p2 = new Paragraph("Historial de mantenimientos", fTitulo);
        p2.setSpacingBefore(2f);
        texto.addElement(p1);
        texto.addElement(p2);
        outer.addCell(texto);
        return outer;
    }

    private static PdfPTable tarjetaVehiculo(
            CabeceraExport cab, Font fSeccion, Font fLabel, Font fValor
    ) throws DocumentException {
        PdfPTable card = new PdfPTable(1);
        card.setWidthPercentage(100);

        PdfPCell wrap = new PdfPCell();
        wrap.setBackgroundColor(GRIS_TARJETA);
        wrap.setBorderColor(GRIS_BORDE);
        wrap.setBorderWidth(1f);
        wrap.setPadding(12f);

        Paragraph titulo = new Paragraph("Vehículo", fSeccion);
        titulo.setSpacingAfter(8f);
        wrap.addElement(titulo);

        PdfPTable grid = new PdfPTable(new float[]{1f, 1f});
        grid.setWidthPercentage(100);
        grid.addCell(celdaDato("Marca / modelo",
                safe(cab.marca()) + " " + safe(cab.modelo()), fLabel, fValor));
        grid.addCell(celdaDato("Placa", safe(cab.placa()), fLabel, fValor));
        grid.addCell(celdaDato("Período",
                cab.fechaDesde().format(DF) + " – " + cab.fechaHasta().format(DF), fLabel, fValor));
        grid.addCell(celdaDato("Fecha de emisión", cab.fechaEmisionLima().format(DF), fLabel, fValor));
        String km = cab.kmVigente() == null ? "No registrado" : String.valueOf(cab.kmVigente()) + " km";
        PdfPCell kmCell = celdaDato("Kilometraje vigente", km, fLabel, fValor);
        kmCell.setColspan(2);
        grid.addCell(kmCell);

        wrap.addElement(grid);
        card.addCell(wrap);
        return card;
    }

    private static PdfPTable resumenVisual(
            List<MantenimientoExport> regs, Font fSeccion, Font fLabel, Font fValor
    ) throws DocumentException {
        int total = regs.size();
        long realizados = regs.stream().filter(m -> "REALIZADO".equalsIgnoreCase(m.estado())).count();
        long anulados = regs.stream().filter(m -> "ANULADO".equalsIgnoreCase(m.estado())).count();

        PdfPTable card = new PdfPTable(1);
        card.setWidthPercentage(100);

        PdfPCell wrap = new PdfPCell();
        wrap.setBackgroundColor(AZUL_SUAVE);
        wrap.setBorderColor(new Color(0x90, 0xCA, 0xF9));
        wrap.setBorderWidth(1f);
        wrap.setPadding(10f);

        Paragraph titulo = new Paragraph("Resumen del período", fSeccion);
        titulo.setSpacingAfter(6f);
        wrap.addElement(titulo);

        boolean mostrarAnulados = anulados > 0;
        PdfPTable grid = new PdfPTable(mostrarAnulados ? new float[]{1f, 1f, 1f} : new float[]{1f, 1f});
        grid.setWidthPercentage(100);
        grid.addCell(celdaDato("Total exportados", String.valueOf(total), fLabel, fValor));
        grid.addCell(celdaDato("Realizados", String.valueOf(realizados), fLabel, fValor));
        if (mostrarAnulados) {
            grid.addCell(celdaDato("Anulados", String.valueOf(anulados), fLabel, fValor));
        }
        wrap.addElement(grid);
        card.addCell(wrap);
        return card;
    }

    private static PdfPCell celdaDato(String label, String valor, Font fLabel, Font fValor) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", fLabel));
        p.add(new Chunk(valor == null || valor.isBlank() ? "—" : valor, fValor));
        PdfPCell c = new PdfPCell(p);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(4f);
        c.setPaddingBottom(6f);
        return c;
    }

    private static PdfPTable bloqueRegistro(
            MantenimientoExport m,
            int n,
            Font fNombre,
            Font fLabel,
            Font fNormal,
            Font fBadgeOk,
            Font fBadgeBad
    ) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6f);
        table.setSpacingAfter(6f);
        table.setKeepTogether(true);

        boolean esAnulado = "ANULADO".equalsIgnoreCase(m.estado());
        Color bg = esAnulado ? new Color(0xFF, 0xF8, 0xF8) : Color.WHITE;

        String tipoMostrar;
        if ("OTRO".equalsIgnoreCase(m.codigoTipo()) && m.servicioOtro() != null && !m.servicioOtro().isBlank()) {
            tipoMostrar = m.servicioOtro().trim();
        } else {
            tipoMostrar = safe(m.nombreTipo());
        }

        PdfPCell wrap = new PdfPCell();
        wrap.setBackgroundColor(bg);
        wrap.setBorderColor(esAnulado ? new Color(0xEF, 0x9A, 0x9A) : GRIS_BORDE);
        wrap.setBorderWidth(1.2f);
        wrap.setPadding(10f);
        wrap.setPaddingTop(8f);

        // Título + badge estado
        PdfPTable head = new PdfPTable(new float[]{4.2f, 1.3f});
        head.setWidthPercentage(100);
        Paragraph nombre = new Paragraph(n + ". " + tipoMostrar, fNombre);
        PdfPCell cNombre = new PdfPCell(nombre);
        cNombre.setBorder(Rectangle.NO_BORDER);
        cNombre.setVerticalAlignment(Element.ALIGN_MIDDLE);
        head.addCell(cNombre);

        PdfPCell badge = new PdfPCell(new Phrase(
                esAnulado ? "ANULADO" : "REALIZADO",
                esAnulado ? fBadgeBad : fBadgeOk));
        badge.setBackgroundColor(esAnulado ? ROJO_BG : VERDE_BG);
        badge.setBorder(Rectangle.NO_BORDER);
        badge.setHorizontalAlignment(Element.ALIGN_CENTER);
        badge.setVerticalAlignment(Element.ALIGN_MIDDLE);
        badge.setPadding(5f);
        badge.setPaddingTop(4f);
        badge.setPaddingBottom(4f);
        head.addCell(badge);
        wrap.addElement(head);

        PdfPTable datos = new PdfPTable(new float[]{1f, 1f});
        datos.setWidthPercentage(100);
        datos.setSpacingBefore(8f);
        datos.addCell(celdaLinea("Fecha",
                m.fechaServicio() == null ? "—" : m.fechaServicio().format(DF), fLabel, fNormal));
        datos.addCell(celdaLinea("Km del servicio",
                m.kmServicio() == null ? "—" : String.valueOf(m.kmServicio()), fLabel, fNormal));
        datos.addCell(celdaLinea("Taller",
                m.nombreTaller() == null || m.nombreTaller().isBlank() ? "Sin taller" : m.nombreTaller().trim(),
                fLabel, fNormal));
        datos.addCell(celdaLinea("Costo", formatoMonto(m.costoTotal()), fLabel, fNormal));
        wrap.addElement(datos);

        if (m.descripcion() != null && !m.descripcion().isBlank()) {
            wrap.addElement(bloqueTexto("Observaciones", m.descripcion().trim(), fLabel, fNormal));
        }
        if (m.recomendaciones() != null && !m.recomendaciones().isBlank()) {
            wrap.addElement(bloqueTexto("Recomendaciones", m.recomendaciones().trim(), fLabel, fNormal));
        }
        if (m.repuestos() != null && !m.repuestos().isEmpty()) {
            Paragraph repTitulo = new Paragraph("Repuestos", fLabel);
            repTitulo.setSpacingBefore(6f);
            wrap.addElement(repTitulo);
            for (RepuestoExport r : m.repuestos()) {
                StringBuilder linea = new StringBuilder("• ").append(safe(r.nombreSnapshot()));
                if (r.marca() != null && !r.marca().isBlank()) {
                    linea.append(" (").append(r.marca().trim()).append(')');
                }
                linea.append("  × ").append(r.cantidad() == null ? "—" : r.cantidad());
                linea.append("  ·  P. unit.: ").append(formatoMonto(r.precioUnitario()));
                Paragraph rp = new Paragraph(linea.toString(), fNormal);
                rp.setSpacingBefore(2f);
                wrap.addElement(rp);
            }
        }

        table.addCell(wrap);
        return table;
    }

    private static PdfPCell celdaLinea(String label, String valor, Font fLabel, Font fNormal) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", fLabel));
        p.add(new Chunk(valor, fNormal));
        PdfPCell c = new PdfPCell(p);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(3f);
        c.setPaddingBottom(4f);
        return c;
    }

    private static Paragraph bloqueTexto(String label, String texto, Font fLabel, Font fNormal) {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(6f);
        p.add(new Chunk(label + "\n", fLabel));
        p.add(new Chunk(texto, fNormal));
        return p;
    }

    private static String formatoMonto(BigDecimal v) {
        if (v == null) {
            return "No informado";
        }
        return "S/ " + String.format(Locale.US, "%,.2f", v);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static final class PiePagina extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte cb = writer.getDirectContent();
                float y = document.bottom() - 22;
                float x1 = document.left();
                float x2 = document.right();
                cb.setColorStroke(GRIS_BORDE);
                cb.setLineWidth(0.6f);
                cb.moveTo(x1, y + 10);
                cb.lineTo(x2, y + 10);
                cb.stroke();

                Phrase left = new Phrase(
                        "MyCar — Historial de mantenimientos",
                        FontFactory.getFont(FontFactory.HELVETICA, 8, GRIS_MUTE));
                Phrase right = new Phrase(
                        "Página " + writer.getPageNumber(),
                        FontFactory.getFont(FontFactory.HELVETICA, 8, GRIS_MUTE));
                com.lowagie.text.pdf.ColumnText.showTextAligned(
                        cb, Element.ALIGN_LEFT, left, x1, y, 0);
                com.lowagie.text.pdf.ColumnText.showTextAligned(
                        cb, Element.ALIGN_RIGHT, right, x2, y, 0);
            } catch (Exception ignored) {
                // pie opcional
            }
        }
    }
}
