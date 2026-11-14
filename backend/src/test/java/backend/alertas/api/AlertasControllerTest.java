package backend.alertas.api;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.model.ErrorAlertas;
import backend.alertas.seguridad.IdentidadAlertas;
import backend.alertas.service.AtenderAlertaService;
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
    AtenderAlertaService atender;
    Authentication auth;

    @BeforeEach @SuppressWarnings("unchecked") void preparar() {
        var proveedor = mock(ObjectProvider.class);
        IdentidadAlertas identidad = mock(IdentidadAlertas.class);
        when(proveedor.getIfAvailable()).thenReturn(identidad);
        when(identidad.exigirUsuario(any())).thenReturn(9L);
        programar = mock(ProgramarAlertaService.class);
        sincronizar = mock(SincronizarAlertasService.class);
        atender = mock(AtenderAlertaService.class);
        auth = UsernamePasswordAuthenticationToken.authenticated("fixture", null, List.of());
        mvc = MockMvcBuilders.standaloneSetup(new AlertasController(programar, sincronizar, atender, proveedor))
                .setControllerAdvice(new ErroresAlertasController()).build();
    }

    @Test void listarSincronizaRf23() throws Exception {
        when(sincronizar.listarMisAlertas(9L, 3L)).thenReturn(new ContratoRf22.ListaResponse(1, List.of()));
        mvc.perform(get("/api/v1/rf22/vehiculos/3/alertas").principal(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alertasSincronizadas").value(1));
    }

    @Test void detalleAlertaRf24() throws Exception {
        var detalle = new ContratoRf22.DetalleAlertaDto(
                12L, "MANTENIMIENTO", "CAMBIO_ACEITE", "Cambio de aceite", "POR_KILOMETRAJE",
                "DESDE_HOY", "PROXIMA", "2026-11-05T00:00:00.000Z", null, 25000, 20000, 5000,
                null, null, null, null, 1L, null, null, 1,
                new ContratoRf22.VehiculoResumenDto(3L, "A1B345", "Toyota", "Corolla", "ACTIVO"),
                new ContratoRf22.AccionesDetalleDto(true, true));
        when(sincronizar.consultarDetalle(9L, 3L, 12L)).thenReturn(detalle);
        mvc.perform(get("/api/v1/rf22/vehiculos/3/alertas/12").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAlerta").value(12))
                .andExpect(jsonPath("$.acciones.atender").value(true))
                .andExpect(jsonPath("$.vehiculo.placa").value("A1B345"));
    }

    @Test void programarDevuelve201() throws Exception {
        UUID op = UUID.randomUUID();
        when(programar.programar(eq(9L), eq(3L), any())).thenReturn(new ContratoRf22.ProgramarResponse(op, 12L, "PROGRAMADA"));
        mvc.perform(post("/api/v1/rf22/vehiculos/3/alertas").principal(auth)
                .contentType("application/json")
                .content("""
                        {"idOperacion":"%s","codigoTipoMantenimiento":"CAMBIO_ACEITE","criterio":"POR_KILOMETRAJE","origenCalculo":"DESDE_HOY","intervaloKm":5000,"kmBaseConfirmado":20000,"idLecturaConfirmada":1}
                        """.formatted(op)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.idAlerta").value(12));
    }

    @Test void equivalenteEs409() throws Exception {
        when(programar.programar(anyLong(), anyLong(), any()))
                .thenThrow(new ErrorAlertas("ALERTA_EQUIVALENTE", "Duplicada"));
        mvc.perform(post("/api/v1/rf22/vehiculos/3/alertas").principal(auth)
                .contentType("application/json")
                .content("""
                        {"idOperacion":"%s","codigoTipoMantenimiento":"CAMBIO_ACEITE","criterio":"POR_KILOMETRAJE","origenCalculo":"DESDE_HOY","intervaloKm":5000,"kmBaseConfirmado":20000,"idLecturaConfirmada":1}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ALERTA_EQUIVALENTE"));
    }

    @Test void contextoIncluyeKm() throws Exception {
        when(programar.contexto(eq(9L), eq(3L), isNull(), isNull())).thenReturn(new ContratoRf22.ContextoResponse(
                LocalDate.of(2026, 10, 28), "America/Lima", 20000, 1L, List.of(), List.of(), true));
        mvc.perform(get("/api/v1/rf22/contexto?idVehiculo=3").principal(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kmActual").value(20000));
        verify(programar).contexto(9L, 3L, null, null);
    }

    @Test void contextoPasaFiltroTipoYServicioOtro() throws Exception {
        when(programar.contexto(eq(9L), eq(3L), eq("OTRO"), eq("Correa"))).thenReturn(new ContratoRf22.ContextoResponse(
                LocalDate.of(2026, 10, 28), "America/Lima", 20000, 1L, List.of(), List.of(), true));
        mvc.perform(get("/api/v1/rf22/contexto")
                        .param("idVehiculo", "3")
                        .param("codigoTipo", "OTRO")
                        .param("servicioOtro", "Correa")
                        .principal(auth))
                .andExpect(status().isOk());
        verify(programar).contexto(9L, 3L, "OTRO", "Correa");
    }

    @Test void listarVehiculoAjenoEs404() throws Exception {
        when(sincronizar.listarMisAlertas(9L, 3L))
                .thenThrow(new ErrorAlertas("VEHICULO_NO_ENCONTRADO", "Vehículo no disponible."));
        mvc.perform(get("/api/v1/rf22/vehiculos/3/alertas").principal(auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VEHICULO_NO_ENCONTRADO"));
    }

    @Test void atenderDevuelve200() throws Exception {
        UUID op = UUID.randomUUID();
        when(atender.atender(eq(9L), eq(3L), eq(12L), any()))
                .thenReturn(new ContratoRf22.AtenderResponse(12L, "REALIZADA", 99L, op));
        mvc.perform(post("/api/v1/rf22/vehiculos/3/alertas/12/atender").principal(auth)
                        .contentType("application/json")
                        .content("""
                                {"idOperacion":"%s","fechaServicio":"2026-11-07","kmServicio":25000,"origenKmServicio":"HISTORICO_MANUAL","idTaller":55}
                                """.formatted(op)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoAlerta").value("REALIZADA"))
                .andExpect(jsonPath("$.idMantenimientoAtencion").value(99));
    }

    @Test void anularDevuelve200() throws Exception {
        UUID op = UUID.randomUUID();
        when(atender.anular(eq(9L), eq(3L), eq(12L), any()))
                .thenReturn(new ContratoRf22.AnularAlertaResponse(12L, "ANULADA", op));
        mvc.perform(post("/api/v1/rf22/vehiculos/3/alertas/12/anular").principal(auth)
                        .contentType("application/json")
                        .content("""
                                {"idOperacion":"%s","motivo":"Ya no aplica"}
                                """.formatted(op)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADA"))
                .andExpect(jsonPath("$.idOperacion").value(op.toString()));
    }

    @Test void atenderAlertaAjenaEs404() throws Exception {
        when(atender.atender(anyLong(), anyLong(), anyLong(), any()))
                .thenThrow(new ErrorAlertas("ALERTA_NO_ENCONTRADA", "La alerta no está disponible."));
        mvc.perform(post("/api/v1/rf22/vehiculos/3/alertas/12/atender").principal(auth)
                        .contentType("application/json")
                        .content("""
                                {"idOperacion":"%s","fechaServicio":"2026-11-07","kmServicio":1,"origenKmServicio":"HISTORICO_MANUAL"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ALERTA_NO_ENCONTRADA"));
    }
}
