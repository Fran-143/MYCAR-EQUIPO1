package backend.notificaciones.api;

import backend.alertas.seguridad.IdentidadAlertas;
import backend.notificaciones.api.dto.ContratoPreferenciasNotificacion;
import backend.notificaciones.model.ErrorNotificaciones;
import backend.notificaciones.service.PreferenciaNotificacionService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PreferenciasNotificacionControllerTest {
    MockMvc mvc;
    PreferenciaNotificacionService servicio;
    Authentication auth;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        var proveedor = mock(ObjectProvider.class);
        IdentidadAlertas identidad = mock(IdentidadAlertas.class);
        when(proveedor.getIfAvailable()).thenReturn(identidad);
        when(identidad.exigirUsuario(any())).thenReturn(12L);
        servicio = mock(PreferenciaNotificacionService.class);
        auth = UsernamePasswordAuthenticationToken.authenticated("u", null, List.of());
        mvc = MockMvcBuilders.standaloneSetup(new PreferenciasNotificacionController(servicio, proveedor))
                .setControllerAdvice(new ErroresPreferenciasNotificacionController())
                .build();
    }

    @Test
    void getOk() throws Exception {
        when(servicio.consultar(12L)).thenReturn(
                new ContratoPreferenciasNotificacion.PreferenciasResponse(
                        true, false, true, "CONCEDIDO"));
        mvc.perform(get("/api/v1/preferencias-notificacion").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mantenimiento").value(true))
                .andExpect(jsonPath("$.soat").value(false))
                .andExpect(jsonPath("$.kilometraje").value(true))
                .andExpect(jsonPath("$.permisoDispositivo").value("CONCEDIDO"));
    }

    @Test
    void putOk() throws Exception {
        when(servicio.guardar(eq(12L), any())).thenReturn(
                new ContratoPreferenciasNotificacion.PreferenciasResponse(
                        false, false, false, "NO_SOLICITADO"));
        mvc.perform(put("/api/v1/preferencias-notificacion")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mantenimiento":false,"soat":false,"kilometraje":false,"permisoDispositivo":"NO_SOLICITADO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mantenimiento").value(false));
    }

    @Test
    void putDatosInvalidos400() throws Exception {
        when(servicio.guardar(eq(12L), any()))
                .thenThrow(new ErrorNotificaciones("DATOS_INVALIDOS", "Permiso de dispositivo no válido."));
        mvc.perform(put("/api/v1/preferencias-notificacion")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mantenimiento":true,"soat":true,"kilometraje":true,"permisoDispositivo":"X"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void patchPermisoOk() throws Exception {
        when(servicio.actualizarPermisoDispositivo(12L, "DENEGADO")).thenReturn(
                new ContratoPreferenciasNotificacion.PreferenciasResponse(
                        true, true, true, "DENEGADO"));
        mvc.perform(patch("/api/v1/preferencias-notificacion/permiso-dispositivo")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permisoDispositivo":"DENEGADO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permisoDispositivo").value("DENEGADO"));
    }
}
