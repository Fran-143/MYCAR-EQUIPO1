package backend.historial.api;

import backend.historial.model.DatosHistorial.*;
import backend.historial.model.HistorialNoDisponible;
import backend.historial.service.HistorialMantenimientoService;
import backend.autenticacion.SessionAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Pruebas de controlador con identidad de prueba; no sustituyen una prueba del login real. */
class HistorialMantenimientoControllerTest {
    private HistorialMantenimientoService servicio;
    private MockMvc mvc;
    private final UsernamePasswordAuthenticationToken sesion =
            UsernamePasswordAuthenticationToken.authenticated(new SessionAuthentication.Principal(
                    7, 1, java.util.UUID.randomUUID(), java.time.Instant.now().plusSeconds(3600)), "", List.of());

    @BeforeEach @SuppressWarnings("unchecked") void preparar() {
        servicio = mock(HistorialMantenimientoService.class);
        mvc = standaloneSetup(new HistorialMantenimientoController(servicio))
                .setControllerAdvice(new ErroresHistorialController()).build();
    }

    @Test void sinSesionRechazaAntesDeConsultar() throws Exception {
        mvc.perform(get("/api/v1/rf18/contexto")).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test void usaIdentidadDeSesionYNoIdUsuarioEnQuery() throws Exception {
        when(servicio.contexto(7)).thenReturn(new Contexto(List.of()));
        mvc.perform(get("/api/v1/rf18/contexto").principal(sesion).param("idUsuario", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vehiculos").isEmpty());
        verify(servicio).contexto(7);
    }

    @Test void principalGenericoNoSustituyeSesionReal() throws Exception {
        var falsa = UsernamePasswordAuthenticationToken.authenticated("7", "", List.of());
        mvc.perform(get("/api/v1/rf18/contexto").principal(falsa))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test void ajenoDevuelveAvisoGenericoSinDatos() throws Exception {
        when(servicio.consultar(7, 99)).thenThrow(new HistorialNoDisponible());
        mvc.perform(get("/api/v1/rf18/vehiculos/99/mantenimientos").principal(sesion))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("VEHICULO_NO_DISPONIBLE"));
    }

    @Test void falloTecnicoNoEsUnaRespuestaDeHistorialVacio() throws Exception {
        when(servicio.consultar(7, 10)).thenThrow(new IllegalStateException("SQL privado"));
        mvc.perform(get("/api/v1/rf18/vehiculos/10/mantenimientos").principal(sesion))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("CONSULTA_NO_DISPONIBLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL privado"))));
    }

    @Test void respuestaHttpPreservaFechaIsoYCostosNullYCero() throws Exception {
        var v = new Vehiculo(10, "ABC123", "Toyota", "Corolla", "ACTIVO", true);
        var fecha = java.time.LocalDate.of(2026, 10, 10);
        when(servicio.consultar(7, 10)).thenReturn(new Resultado(v, 2, List.of(
                new Mantenimiento(21, "OTRO", "Otro", "Alineación", fecha, 49000, "ANULADO", java.math.BigDecimal.ZERO),
                new Mantenimiento(20, "CAMBIO_ACEITE", "Cambio de aceite", null, fecha.minusDays(1), 47000, "REALIZADO", null))));
        mvc.perform(get("/api/v1/rf18/vehiculos/10/mantenimientos").principal(sesion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.mantenimientos[0].fechaServicio").value("2026-10-10"))
                .andExpect(jsonPath("$.mantenimientos[0].costoTotal").value(0))
                .andExpect(jsonPath("$.mantenimientos[1].costoTotal").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test void APIAusentePorDefectoYActivablePorPropiedad() {
        var runner = new ApplicationContextRunner().withUserConfiguration(HistorialMantenimientoController.class)
                .withBean(HistorialMantenimientoService.class, () -> servicio);
        runner.run(c -> assertTrue(c.getBeansOfType(HistorialMantenimientoController.class).isEmpty()));
        runner.withPropertyValues("mycar.rf18.api-habilitada=true")
                .run(c -> assertEquals(1, c.getBeansOfType(HistorialMantenimientoController.class).size()));
    }
}
