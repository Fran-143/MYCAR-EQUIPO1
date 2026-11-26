package backend.vehiculos.service;

import backend.fotos.FotoVehiculoService;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.DatosDetalleVehiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompartirVehiculoPdfServiceTest {
    private ConsultaRegistroVehiculoService consultas;
    private FotoVehiculoService fotos;
    private CompartirVehiculoPdfService servicio;

    @BeforeEach
    void setUp() {
        consultas = mock(ConsultaRegistroVehiculoService.class);
        fotos = mock(FotoVehiculoService.class);
        servicio = new CompartirVehiculoPdfService(consultas, fotos, new CompartirVehiculoGate());
    }

    @Test
    void activoSinFotoGeneraPdfSoloCamposPublicos() {
        when(consultas.consultarDetalle(7L, 1L)).thenReturn(detalle(false, "Gasolina", false));
        var pdf = servicio.generar(7L, 1L, false);
        assertTrue(pdf.bytes().length > 100);
        assertEquals("MyCar-vehiculo.pdf", pdf.nombreArchivo());
        assertTrue(contieneEnPdf(pdf.bytes(), "Toyota"));
        assertTrue(contieneEnPdf(pdf.bytes(), "Corolla"));
        assertTrue(contieneEnPdf(pdf.bytes(), "Gasolina"));
        assertTrue(contieneEnPdf(pdf.bytes(), "Ficha pública del vehículo"));
        assertTrue(contieneEnPdf(pdf.bytes(), "Documento informativo generado por MyCar"));
        assertFalse(contieneEnPdf(pdf.bytes(), "ABC-123"));
        assertFalse(contieneEnPdf(pdf.bytes(), "1HGBH41JXMN109186"));
        assertFalse(contieneEnPdf(pdf.bytes(), "SERIE-X"));
        verify(fotos, never()).leerBytes(anyLong(), anyLong());
    }

    @Test
    void inactivoTambienPuedeCompartir() {
        when(consultas.consultarDetalle(7L, 2L)).thenReturn(detalleInactivo());
        var pdf = servicio.generar(7L, 2L, false);
        assertTrue(pdf.bytes().length > 100);
    }

    @Test
    void electricoMuestraNoAplica() {
        when(consultas.consultarDetalle(7L, 1L)).thenReturn(detalle(true, null, false));
        var pdf = servicio.generar(7L, 1L, false);
        assertTrue(contieneEnPdf(pdf.bytes(), "No aplica"));
    }

    @Test
    void incluirFotoSinPersonalizadaUsaPredeterminada() {
        when(consultas.consultarDetalle(7L, 1L)).thenReturn(detalle(false, "Gasolina", false));
        var pdf = servicio.generar(7L, 1L, true);
        assertTrue(pdf.bytes().length > 1000);
        verify(fotos, never()).leerBytes(anyLong(), anyLong());
    }

    @Test
    void incluirFotoConPersonalizada() throws Exception {
        when(consultas.consultarDetalle(7L, 1L)).thenReturn(detalle(false, "Gasolina", true));
        when(fotos.leerBytes(7L, 1L)).thenReturn(imagenJpeg());
        var pdf = servicio.generar(7L, 1L, true);
        assertTrue(pdf.bytes().length > 1000);
    }

    @Test
    void errorAlLeerFotoNoEntregaPdfIncompleto() {
        when(consultas.consultarDetalle(7L, 1L)).thenReturn(detalle(false, "Gasolina", true));
        when(fotos.leerBytes(7L, 1L)).thenThrow(backend.fotos.FotoException.storage("down"));
        ErrorRegistroVehiculo ex = assertThrows(ErrorRegistroVehiculo.class,
                () -> servicio.generar(7L, 1L, true));
        assertEquals("PDF_NO_GENERADO", ex.codigo());
    }

    @Test
    void ajenoPropagadoComoNoEncontrado() {
        when(consultas.consultarDetalle(7L, 99L))
                .thenThrow(new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO"));
        ErrorRegistroVehiculo ex = assertThrows(ErrorRegistroVehiculo.class,
                () -> servicio.generar(7L, 99L, false));
        assertEquals("VEHICULO_NO_ENCONTRADO", ex.codigo());
    }

    @Test
    void etiquetaCombustibleElectrico() {
        assertEquals("No aplica", CompartirVehiculoPdfService.etiquetaCombustible(
                detalle(true, "Gasolina", false)));
        assertEquals("Gasolina", CompartirVehiculoPdfService.etiquetaCombustible(
                detalle(false, "Gasolina", false)));
    }

    private static boolean contieneEnPdf(byte[] pdf, String texto) {
        return new String(pdf, StandardCharsets.ISO_8859_1).contains(texto);
    }

    private static DatosDetalleVehiculo detalle(boolean electrico, String combustible, boolean tieneFoto) {
        return new DatosDetalleVehiculo(
                1L, "ABC-123", "Toyota", "Corolla", 2021, "Particular", "Blanco",
                electrico ? "Eléctrico" : "Combustión", combustible, "ACTIVO", true,
                BigDecimal.valueOf(8240), null, "1HGBH41JXMN109186", "SERIE-X", "MOTOR-Y",
                null, electrico, "PARTICULAR", 1L, 1L, null, null, null, null, false, tieneFoto
        );
    }

    private static DatosDetalleVehiculo detalleInactivo() {
        return new DatosDetalleVehiculo(
                2L, "XYZ-999", "Kia", "Rio", 2018, "Particular", "Rojo",
                "Combustión", "Gasolina", "INACTIVO", false,
                BigDecimal.ZERO, null, "VINPRIVADO", "SER", "MOT",
                null, false, "PARTICULAR", 1L, 1L, null, null, null, null, false, false
        );
    }

    private static byte[] imagenJpeg() throws Exception {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 32, 32);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}
