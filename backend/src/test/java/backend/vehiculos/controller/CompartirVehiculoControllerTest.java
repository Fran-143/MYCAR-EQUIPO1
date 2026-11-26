package backend.vehiculos.controller;

import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.service.CompartirVehiculoPdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompartirVehiculoControllerTest {
    private MockMvc mvc;
    private CompartirVehiculoPdfService pdfService;
    private Authentication autenticacion;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        ObjectProvider<IdentidadVehiculos> proveedores = mock(ObjectProvider.class);
        IdentidadVehiculos identidad = mock(IdentidadVehiculos.class);
        pdfService = mock(CompartirVehiculoPdfService.class);
        autenticacion = UsernamePasswordAuthenticationToken.authenticated("fixture", null, List.of());
        when(proveedores.getIfAvailable()).thenReturn(identidad);
        when(identidad.exigirUsuarioAutenticado(any())).thenReturn(17L);
        mvc = MockMvcBuilders.standaloneSetup(new CompartirVehiculoController(proveedores, pdfService))
                .setControllerAdvice(new ErroresVehiculosController())
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void pdfExitosoSinFoto() throws Exception {
        when(pdfService.generar(17L, 81L, false))
                .thenReturn(new CompartirVehiculoPdfService.PdfGenerado(
                        "%PDF-1.4 test".getBytes(), "MyCar-vehiculo.pdf"));
        mvc.perform(get("/api/v1/vehiculos/81/compartir/pdf").principal(autenticacion))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition",
                        containsString("MyCar-vehiculo.pdf")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        verify(pdfService).generar(17L, 81L, false);
    }

    @Test
    void pdfConIncluirFoto() throws Exception {
        when(pdfService.generar(17L, 81L, true))
                .thenReturn(new CompartirVehiculoPdfService.PdfGenerado(
                        "%PDF-1.4".getBytes(), "MyCar-vehiculo.pdf"));
        mvc.perform(get("/api/v1/vehiculos/81/compartir/pdf?incluirFoto=true")
                        .principal(autenticacion))
                .andExpect(status().isOk());
        verify(pdfService).generar(17L, 81L, true);
    }

    @Test
    void ajenoDevuelve404SinDetalles() throws Exception {
        when(pdfService.generar(17L, 99L, false))
                .thenThrow(new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO"));
        mvc.perform(get("/api/v1/vehiculos/99/compartir/pdf").principal(autenticacion))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("codigo").value("VEHICULO_NO_ENCONTRADO"));
    }

    @Test
    void errorPdfDevuelve503() throws Exception {
        when(pdfService.generar(17L, 81L, true))
                .thenThrow(new ErrorRegistroVehiculo("PDF_NO_GENERADO"));
        mvc.perform(get("/api/v1/vehiculos/81/compartir/pdf?incluirFoto=true")
                        .principal(autenticacion))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("codigo").value("PDF_NO_GENERADO"));
    }

    @Test
    void sinSesion401() throws Exception {
        mvc.perform(get("/api/v1/vehiculos/81/compartir/pdf"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("codigo").value("SESION_NO_VALIDA"));
        verifyNoInteractions(pdfService);
    }

    private static org.hamcrest.Matcher<String> containsString(String s) {
        return org.hamcrest.Matchers.containsString(s);
    }
}
