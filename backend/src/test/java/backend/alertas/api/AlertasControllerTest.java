package backend.alertas.api;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.model.ErrorAlertas;
import backend.alertas.seguridad.IdentidadAlertas;
import backend.alertas.service.ProgramarAlertaService;
import backend.alertas.service.SincronizarAlertasService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AlertasControllerTest {
    MockMvc mvc;
    ProgramarAlertaService programar;
    SincronizarAlertasService sincronizar;
    Authentication auth;

    @BeforeEach @SuppressWarnings("unchecked") void preparar() {
        var proveedor = mock(ObjectProvider.class);
        IdentidadAlertas identidad = mock(IdentidadAlertas.class);
        when(proveedor.getIfAvailable()).thenReturn(identidad);
        when(identidad.exigirUsuario(any())).thenReturn(9L);
        programar = mock(ProgramarAlertaService.class);
        sincronizar = mock(SincronizarAlertasService.class);
        auth = UsernamePasswordAuthenticationToken.authenticated("fixture", null, List.of());
        mvc = MockMvcBuilders.standaloneSetup(new AlertasController(programar, sincronizar, proveedor))
                .setControllerAdvice(new ErroresAlertasController()).build();
    }

    @Test void listarSincronizaRf23() throws Exception {
        when(sincronizar.listarMisAlertas(9L, 3L)).thenReturn(new ContratoRf22.ListaResponse(1, List.of()));
        mvc.perform(get("/api/v1/rf22/vehiculos/3/alertas").principal(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alertasSincronizadas").value(1));
    }

    @Test void programarDevuelve201() throws Exception {
        UUID op = UUID.randomUUID();
        when(programar.programar(eq(9L), eq(3L), any())).thenReturn(new ContratoRf22.ProgramarResponse(op, 12L, "PROGRAMADA"));
        mvc.perform(post("/api/v1/rf22/vehiculos/3/alertas").principal(auth)
                .contentType("application/json")
                .content("""
                        {"idOperacion":"%s","codigoTipoMantenimiento":"CAMBIO_ACEITE","criterio":"POR_KILOMETRAJE","intervaloKm":5000}
                        """.formatted(op)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.idAlerta").value(12));
    }

    @Test void equivalenteEs409() throws Exception {
        when(programar.programar(anyLong(), anyLong(), any()))
                .thenThrow(new ErrorAlertas("ALERTA_EQUIVALENTE", "Duplicada"));
        mvc.perform(post("/api/v1/rf22/vehiculos/3/alertas").principal(auth)
                .contentType("application/json")
                .content("""
                        {"idOperacion":"%s","codigoTipoMantenimiento":"CAMBIO_ACEITE","criterio":"POR_KILOMETRAJE","intervaloKm":5000}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ALERTA_EQUIVALENTE"));
    }

    @Test void contextoIncluyeKm() throws Exception {
        when(programar.contexto(9L, 3L)).thenReturn(new ContratoRf22.ContextoResponse(
                LocalDate.of(2026, 10, 28), "America/Lima", 20000, 1L, List.of(), List.of(), true));
        mvc.perform(get("/api/v1/rf22/contexto?idVehiculo=3").principal(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kmActual").value(20000));
    }
}
