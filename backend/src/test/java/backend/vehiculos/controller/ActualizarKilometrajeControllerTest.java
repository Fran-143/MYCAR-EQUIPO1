package backend.vehiculos.controller;

import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.ContextoKilometrajeActual;
import backend.vehiculos.dto.DatosActualizarKilometraje;
import backend.vehiculos.dto.ResultadoActualizarKilometraje;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.service.ActualizarKilometrajeService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ActualizarKilometrajeControllerTest {
    MockMvc mvc;
    ActualizarKilometrajeService servicio;
    Authentication auth;

    @BeforeEach @SuppressWarnings("unchecked") void preparar() {
        var proveedor = mock(ObjectProvider.class);
        IdentidadVehiculos identidad = mock(IdentidadVehiculos.class);
        when(proveedor.getIfAvailable()).thenReturn(identidad);
        when(identidad.exigirUsuarioAutenticado(any())).thenReturn(17L);
        servicio = mock(ActualizarKilometrajeService.class);
        auth = UsernamePasswordAuthenticationToken.authenticated("fixture", null, List.of());
        mvc = MockMvcBuilders.standaloneSetup(new KilometrajeVehiculoController(proveedor, servicio))
                .setControllerAdvice(new ErroresVehiculosController()).build();
    }

    @Test void consultaActualUsaPropietarioDeSesion() throws Exception {
        when(servicio.consultarActual(17, 5)).thenReturn(new ContextoKilometrajeActual(
                5, 9, 50000, Instant.parse("2020-01-01T00:00:00Z"), 0, "ACTIVO"));
        mvc.perform(get("/api/v1/vehiculos/5/kilometraje/actual").principal(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kmActual").value(50000));
        verify(servicio).consultarActual(17, 5);
    }

    @Test void kmNoSuperiorEs409() throws Exception {
        UUID op = UUID.randomUUID();
        when(servicio.registrar(eq(17L), eq(5L), any(DatosActualizarKilometraje.class)))
                .thenThrow(new ErrorRegistroVehiculo("KM_NO_SUPERIOR", Set.of("kmNuevo")));
        mvc.perform(post("/api/v1/vehiculos/5/kilometraje/lecturas").principal(auth)
                .contentType("application/json")
                .content("""
                        {"idOperacion":"%s","kmNuevo":50000,"idLecturaBase":1,"versionBase":0}
                        """.formatted(op)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("KM_NO_SUPERIOR"));
    }
}
